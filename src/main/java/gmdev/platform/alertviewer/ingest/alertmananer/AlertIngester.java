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
    AlertManagerClient alertManagerClient;

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
        boolean oneSuccess = false;
        for (AlertManagerConfig c:state.getAlertmanagers()) {
            boolean b = ingest(c);
            if (b) oneSuccess = true;
            silences.addAll(getSilences(c));
        }
        if (oneSuccess) state.setLastIngestSuccess();
    }



    private boolean isFlappingExpired(AlertManagerEntry entry) {
        Instant i = Instant.ofEpochMilli(entry.getLastChange());
        Instant now = Instant.now();
        boolean b = i.isBefore(now.minus(Duration.ofMinutes(Integer.parseInt(env.getProperty("flapping.timeout.minutes", "15")))));
        long delta = (now.toEpochMilli() - i.toEpochMilli()) / 1000;
        log.debug("Checking "+entry.getId()+" for flapping expired: lastchange="+i.toString()+", now="+now+", deltasecs="+delta+", return "+b);
        return b;
    }

    public Alert jsonToAlert(String jsonAlert) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper.readValue(jsonAlert, Alert.class);
    }

    public boolean ingest(AlertManagerConfig amConfig) {
        log.info("AlertManager Ingester running for alertmanager "+amConfig.getName());
        state.getAlertManagersAll().add(amConfig.getName());
        try {
            HttpRequest request = HttpRequest.newBuilder().timeout(Duration.ofSeconds(10))
                    .uri(new URI(amConfig.getAlertsUrl()))
                    .GET()
                    .build();

            JSONObject json = alertManagerClient.sendRequest(state, amConfig, request);
            if (json == null) {
                return false;
            }

            List<AlertManagerEntry> allActiveMinusDatabased = repo.findAll();
            log.debug("Found "+allActiveMinusDatabased.size()+" alerts in database at start of ingest");

            //process the incoming active alerts
            JSONArray data = json.getJSONArray("data");
            for (int i = 0;i < data.length();i++) {
                String jsonAlert = data.getJSONObject(i).toString();
                Alert alertFromAlertmanager = jsonToAlert(jsonAlert);
                log.debug("Ingest processing alert from alertmanager: "+alertFromAlertmanager.getFingerprint());
                Optional<AlertManagerEntry> alertFromDatabase = repo.findByIdAndAlertmanager(alertFromAlertmanager.getFingerprint(), amConfig.getName());
                AlertManagerEntry databaseEntry;
                if (alertFromDatabase.isPresent()) {
                    databaseEntry = alertFromDatabase.get();
                    databaseEntry.setAlert(alertFromAlertmanager);

                    //RESOLVED alert firing again...
                    if (LogEntryStatus.RESOLVED.equals(databaseEntry.getStatus())) {
                        log.debug("Resolved alert is firing again");

                        //is it flapping?
                        if (!databaseEntry.isAcked() && !isFlappingExpired(databaseEntry)) {
                            databaseEntry.addNote("System", "Alert is FLAPPING");
                            log.info("Firing alert "+databaseEntry.getId()+" is flapping");
                            databaseEntry.setFlapping(true);
                        } else if (isFlappingExpired(databaseEntry)) {
                            log.debug("Firing alert "+databaseEntry.getId()+", which was RESOLVED, is not flapping due to time elapsed");
                        } else {
                            log.debug("else case");
                        }

                        databaseEntry.addNote("System", "Previously RESOLVED alert is now NEW");
                        databaseEntry.setStatus(LogEntryStatus.NEW); //changes lastchange
                    }

                    //firing alert is suppressed...
                    if ("suppressed".equals(alertFromAlertmanager.getStatus().getState())) {
                        if (!LogEntryStatus.SILENCED.equals(databaseEntry.getStatus())) {
                            databaseEntry.addNote("System", "Alert is SILENCED");
                            log.debug("Alert is silenced");
                            if (databaseEntry.isFlapping()) {
                                log.info("Silenced alert " + databaseEntry.getId() + " is no longer flapping(1)");
                                databaseEntry.addNote("System", "Alert is no longer FLAPPING");
                                databaseEntry.setFlapping(false);
                            }
                            databaseEntry.setStatus(LogEntryStatus.SILENCED); //changes lastchange
                        }

                    //SILENCED alert is no longer suppressed...
                    } else if (LogEntryStatus.SILENCED.equals(databaseEntry.getStatus())) {
                        if (!"suppressed".equals(alertFromAlertmanager.getStatus().getState())) {
                            databaseEntry.addNote("System", "Previously SILENCED alert is now NEW");
                            if (databaseEntry.isFlapping()) {
                                log.info("Firing alert " + databaseEntry.getId() + " is no longer flapping(2)");
                                databaseEntry.addNote("System", "Alert is no longer FLAPPING");
                                databaseEntry.setFlapping(false);
                            }
                            databaseEntry.setStatus(LogEntryStatus.NEW); //changes lastchange
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
            log.debug("Database still has "+allActiveMinusDatabased.size()+" alerts that were not present in request");
            for (AlertManagerEntry entry:allActiveMinusDatabased) {
                if (!LogEntryStatus.RESOLVED.equals(entry.getStatus()) && amConfig.getName().equals(entry.getAlertmanager())) {
                    if (LogEntryStatus.NEW.equals(entry.getStatus()) && entry.isFlapping() && !isFlappingExpired(entry)) {
                        log.debug("Firing alert "+entry.getId()+" is resolved but is flapping, keep it as new");
                        entry.setFlapping(true);
                    } else {
                        if (entry.isFlapping()) {
                            log.info("Resolved alert "+entry.getId()+" is no longer flapping(4)");
                            entry.addNote("System", "Alert is no longer FLAPPING");
                            entry.setFlapping(false);
                        }
                        entry.setStatus(LogEntryStatus.RESOLVED); //changes lastchange
                        entry.addNote("System", "Alert is now RESOLVED");
                        log.debug("This alert is now resolved");
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

            return true;
        } catch(Exception e) {
            log.error("Error reading alerts from "+amConfig.getName(), e);
            state.getAlertManagersUp().remove(amConfig.getName());
            return false;
        }
    }

    public List<Silence> getSilences(AlertManagerConfig amConfig) {
        List<Silence> existingSilences = new ArrayList<>();
        try {
            JSONObject json = alertManagerClient.getSilences(state, amConfig);
            if (json == null) return existingSilences;
            JSONArray silences = json.getJSONArray("data");
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
