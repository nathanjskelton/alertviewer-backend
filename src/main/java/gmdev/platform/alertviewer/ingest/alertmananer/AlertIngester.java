package gmdev.platform.alertviewer.ingest.alertmananer;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.client.result.DeleteResult;
import gmdev.platform.alertviewer.data.AlertManagerConfig;
import gmdev.platform.alertviewer.data.AlertManagerEntry;
import gmdev.platform.alertviewer.data.AlertManagerEntryRepo;
import gmdev.platform.alertviewer.data.MetaDataHelper;
import gmdev.platform.alertviewer.data.alert.Alert;
import gmdev.platform.alertviewer.data.silence.Silence;
import gmdev.platform.alertviewer.ingest.Ingester;
import gmdev.platform.alertviewer.server.CustomDateDeserializer;
import gmdev.platform.alertviewer.server.StateBuffer;
import gmdev.platform.alertviewer.util.LogEntryStatus;
import gmdev.platform.alertviewer.util.SSLContextFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(value = "ingester.type", havingValue = "alertmanager")
public class AlertIngester implements Ingester {

    private static final Logger log = LoggerFactory.getLogger(AlertIngester.class);

    @Autowired
    Environment env;

    @Autowired
    SSLContextFactory sslContextFactory;

    @Autowired
    MetaDataHelper meta;

    @Autowired
    MongoTemplate mongo;

    @Autowired
    AlertManagerEntryRepo repo;

    @Autowired
    StateBuffer state;

    @Override
    public void ingest() {
        List<Silence> silences = new ArrayList<>();
        for (AlertManagerConfig c:state.getAlertmanagers()) {
            ingest(c);
            silences.addAll(getSilences(c));
        }
        state.setSilences(silences);
    }


    private JSONObject sendRequest(AlertManagerConfig amConfig, HttpRequest request) {
        JSONObject json = null;

        java.net.http.HttpClient http = null;
        try {
            http = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).sslContext(sslContextFactory.getSSLContext()).build();
        } catch(Exception e) {
            log.error("Unable to get HTTP connection during ingest ("+amConfig.getName()+")", e);
            state.getAlertManagersUp().remove(amConfig.getName());
            return null;
        }

        java.net.http.HttpResponse<String> response = null;
        int tries = 0;
        boolean success = false;
        while (tries < 5 && !success) {
            tries++;
            try {
                response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                success = true;
            } catch (HttpConnectTimeoutException toe) {
                log.warn("Connection timed out connecting to "+amConfig.getName()+" during attempt "+tries);
            } catch (Exception e) {
                log.error("Fatal error connecting to " + request.uri() + " during ingest (" + amConfig.getName() + ")", e);
                state.getAlertManagersUp().remove(amConfig.getName());
                return null;
            }
        }
        if (!success) {
            log.error("Exhausted all attempts to connect to "+amConfig.getName());
            state.getAlertManagersUp().remove(amConfig.getName());
            return  null;
        }

        if (response.statusCode() != 200) {
            log.error("Response code != 200 connecting to " + request.uri() + " during ingest (" + amConfig.getName() + ")");
            state.getAlertManagersUp().remove(amConfig.getName());
            return null;
        }

        try {
            json = new JSONObject(response.body());
        } catch(Exception e) {
            if (response != null) {
                log.error("Unable to parse JSON during ingest ("+amConfig.getName()+"): " + response.body(), e);
            } else {
                log.error("Unable to parse JSON during ingest ("+amConfig.getName()+"): response is null", e);
            }
            state.getAlertManagersUp().remove(amConfig.getName());
            return null;
        }

        return json;
    }

    private boolean isFlappingExpired(AlertManagerEntry entry) {
        Instant i = Instant.ofEpochMilli(entry.getLastChange());
        return i.isBefore(Instant.now().minus(Duration.ofMinutes(Integer.parseInt(env.getProperty("flapping.timeout.minutes", "15")))));
    }

    public void ingest(AlertManagerConfig amConfig) {
        log.info("AlertManager Ingester running for alertmanager "+amConfig.getName());
        state.getAlertManagersAll().add(amConfig.getName());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(amConfig.getAlertsUrl()))
                    .GET()
                    .build();

            JSONObject json = sendRequest(amConfig, request);
            if (json == null) {
                return;
            }

            List<AlertManagerEntry> allActiveMinusDatabased = repo.findAll();

            //process the incoming active alerts
            JSONArray data = json.getJSONArray("data");
            for (int i = 0;i < data.length();i++) {
                String jsonAlert = data.getJSONObject(i).toString();
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                Alert alertFromAlertmanager = objectMapper.readValue(jsonAlert, Alert.class);
                Optional<AlertManagerEntry> alertFromDatabase = repo.findByIdAndAlertmanager(alertFromAlertmanager.getFingerprint(), amConfig.getName());
                AlertManagerEntry databaseEntry;
                if (alertFromDatabase.isPresent()) {
                    databaseEntry = alertFromDatabase.get();
                    databaseEntry.setAlert(alertFromAlertmanager);

                    //RESOLVED alert firing again...
                    if (LogEntryStatus.RESOLVED.equals(databaseEntry.getStatus())) {
                        databaseEntry.addNote("System", "Previously RESOLVED alert is now NEW");
                        databaseEntry.setStatus(LogEntryStatus.NEW);

                        //is it flapping?
                        if (!databaseEntry.isAcked() && !isFlappingExpired(databaseEntry)) {
                            databaseEntry.addNote("System", "Alert is FLAPPING");
                            log.info("Firing alert "+databaseEntry.getId()+" is flapping");
                            databaseEntry.setFlapping(true);
                        } else if (isFlappingExpired(databaseEntry)) {
                            log.debug("Firing alert "+databaseEntry.getId()+", which was RESOLVED, is not flapping due to time elapsed");
                        }
                    }

                    //firing alert is suppressed...
                    if ("suppressed".equals(alertFromAlertmanager.getStatus().getState())) {
                        if (!LogEntryStatus.SILENCED.equals(databaseEntry.getStatus())) {
                            databaseEntry.addNote("System", "Alert is SILENCED");
                            databaseEntry.setStatus(LogEntryStatus.SILENCED);
                            if (databaseEntry.isFlapping()) {
                                log.info("Silenced alert " + databaseEntry.getId() + " is no longer flapping(1)");
                                databaseEntry.addNote("System", "Alert is no longer FLAPPING");
                                databaseEntry.setFlapping(false);
                            }
                        }

                    //SILENCED alert is no longer suppressed...
                    } else if (LogEntryStatus.SILENCED.equals(databaseEntry.getStatus())) {
                        if (!"suppressed".equals(alertFromAlertmanager.getStatus().getState())) {
                            databaseEntry.addNote("System", "Previously SILENCED alert is now NEW");
                            databaseEntry.setStatus(LogEntryStatus.NEW);
                            if (databaseEntry.isFlapping()) {
                                log.info("Firing alert " + databaseEntry.getId() + " is no longer flapping(2)");
                                databaseEntry.addNote("System", "Alert is no longer FLAPPING");
                                databaseEntry.setFlapping(false);
                            }
                        }

                    //NEW alert is still firing...
                    } else if (LogEntryStatus.NEW.equals(databaseEntry.getStatus())) {
                        //should flapping be cleared?
                        if (isFlappingExpired(databaseEntry) && databaseEntry.isFlapping()) {
                            log.info("Firing alert "+databaseEntry.getId()+" is no longer flapping(3)");
                            databaseEntry.addNote("System", "Alert is no longer FLAPPING");
                            databaseEntry.setFlapping(false);
                        //} else {
                            //log.debug("Firing alert "+databaseEntry.getId()+" is still flapping");
                        }
                    }

                    allActiveMinusDatabased.remove(databaseEntry);
                } else {
                    databaseEntry = new AlertManagerEntry(alertFromAlertmanager);
                    databaseEntry.addNote("System", "New alert imported from "+amConfig.getName());
                }
                databaseEntry.setAlertmanager(amConfig.getName());
                repo.save(databaseEntry);
            }

            //process existing alerts that are not existing
            for (AlertManagerEntry entry:allActiveMinusDatabased) {
                if (!LogEntryStatus.RESOLVED.equals(entry.getStatus()) && amConfig.getName().equals(entry.getAlertmanager())) {
                    if (LogEntryStatus.NEW.equals(entry.getStatus()) && entry.isFlapping() && !isFlappingExpired(entry)) {
                        log.debug("Firing alert "+entry.getId()+" is resolved but is flapping, keep it as new");
                        entry.setFlapping(true);
                    } else {
                        entry.setStatus(LogEntryStatus.RESOLVED);
                        if (entry.isFlapping()) {
                            log.info("Resolved alert "+entry.getId()+" is no longer flapping(4)");
                            entry.addNote("System", "Alert is no longer FLAPPING");
                            entry.setFlapping(false);
                        }
                        entry.addNote("System", "Alert is now RESOLVED");
                        repo.save(entry);
                    }
                }
            }

            //timeout resolved alerts
            LocalDateTime date = LocalDateTime.now().minusMinutes(Integer.parseInt(env.getProperty("resolved.remove.minutes", "10080")));
            Query query = new Query();
            Criteria criteria = Criteria.where("status").is(LogEntryStatus.RESOLVED).andOperator(Criteria.where("alert.endsAt").lte(date));
            query.addCriteria(criteria);
            DeleteResult dr = mongo.remove(query, AlertManagerEntry.class);
            if (dr.getDeletedCount() > 0) {
                log.info(dr.getDeletedCount() + " RESOLVED alerts were deleted after "+env.getProperty("resolved.remove.minutes", "10080")+" minutes");
            }
            state.getAlertManagersUp().add(amConfig.getName());

        } catch(Exception e) {
            log.error("Error reading alerts from "+amConfig.getName(), e);
            state.getAlertManagersUp().remove(amConfig.getName());
        }
    }


    public List<Silence> getSilences(AlertManagerConfig amConfig) {
        List<Silence> existingSilences = new ArrayList<>();
        try {
            java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder().sslContext(sslContextFactory.getSSLContext()).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(amConfig.getSilencesUrl()))
                    .GET()
                    .build();

            JSONObject silencesJson = sendRequest(amConfig, request);
            if (silencesJson == null) {
                return existingSilences;
            }

            JSONArray silences = silencesJson.getJSONArray("data");
            for (int i = 0;i < silences.length();i++) {
                String jsonSilence = silences.getJSONObject(i).toString();
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                final SimpleModule module = new SimpleModule("", Version.unknownVersion());
                module.addDeserializer(LocalDateTime.class, new CustomDateDeserializer());
                objectMapper.registerModule(module);
                Silence silence = objectMapper.readValue(jsonSilence, Silence.class);
                silence.setAlertmanager(amConfig.getName());
                if (silence.getStatus().getState().equals("active")) {
                    //set hours based on dates
                    Duration dur = Duration.between(silence.getStartsat(), silence.getEndsat());
                    silence.setHours(dur.toHours());
                    Duration rem = Duration.between(LocalDateTime.now(), silence.getEndsat());
                    silence.setHoursLeft(rem.toHours());
                    existingSilences.add(silence);
                }
            }
            //log.debug("SILENCES ADDED: "+existingSilences.size());
        } catch(Exception e) {
            log.error("Error reading silences from "+amConfig.getName(), e);
        }
        return existingSilences;

    }

}
