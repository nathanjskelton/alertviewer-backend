package gmdev.platform.alertviewer.ingest.alertmananer;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpRequest;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
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

    public void ingest(AlertManagerConfig amConfig) {
        log.info("AlertManager Ingester running for alertmanager "+amConfig.getName());
        try {
            java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder().sslContext(sslContextFactory.getSSLContext()).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(amConfig.getAlertsUrl()))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());

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
                    if (LogEntryStatus.RESOLVED.equals(databaseEntry.getStatus())) {
                        databaseEntry.addNote("System", "Previously RESOLVED alert is now NEW");
                        databaseEntry.setStatus(LogEntryStatus.NEW);
                        if (!databaseEntry.isAcked()) {
                            databaseEntry.setFlapping(true);
                        }
                    }
                    if ("suppressed".equals(alertFromAlertmanager.getStatus().getState())) {
                        if (!LogEntryStatus.SILENCED.equals(databaseEntry.getStatus())) {
                            databaseEntry.addNote("System", "Alert is SILENCED");
                            databaseEntry.setStatus(LogEntryStatus.SILENCED);
                        }
                    } else if (LogEntryStatus.SILENCED.equals(databaseEntry.getStatus())) {
                        if (!"suppressed".equals(alertFromAlertmanager.getStatus().getState())) {
                            databaseEntry.addNote("System", "Previously SILENCED alert is now NEW");
                            databaseEntry.setStatus(LogEntryStatus.NEW);
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
                    entry.setStatus(LogEntryStatus.RESOLVED);
                    entry.addNote("System", "Alert is now RESOLVED");
                    repo.save(entry);
                }
            }

            //timeout resolved alerts
            //timeout resolved alerts
            LocalDateTime date = LocalDateTime.now().minusDays(7);
            Query query = new Query();
            Criteria criteria = Criteria.where("status").is(LogEntryStatus.RESOLVED).andOperator(Criteria.where("alert.endsAt").lte(date));
            query.addCriteria(criteria);
            mongo.remove(query, AlertManagerEntry.class);

        } catch(Exception e) {
            log.error("Error reading alerts from "+amConfig.getName(), e);
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

            java.net.http.HttpResponse<String> response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            JSONObject silencesJson = new JSONObject(response.body());

            log.debug("SILENCES JSON: "+silencesJson.toString());

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
            log.debug("SILENCES ADDED: "+existingSilences.size());
        } catch(Exception e) {
            log.error("Error reading silences from "+amConfig.getName(), e);
        }
        return existingSilences;

    }

}
