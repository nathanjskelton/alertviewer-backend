package gmdev.platform.logviewer.ingest.alertmananer;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import gmdev.platform.logviewer.data.alert.Alert;
import gmdev.platform.logviewer.data.AlertManagerEntry;
import gmdev.platform.logviewer.data.AlertManagerRepo;
import gmdev.platform.logviewer.data.MetaDataHelper;
import gmdev.platform.logviewer.data.silence.Silence;
import gmdev.platform.logviewer.ingest.EntryProcessor;
import gmdev.platform.logviewer.ingest.Ingester;
import gmdev.platform.logviewer.ingest.elastic.Parser;
import gmdev.platform.logviewer.server.CustomDateDeserializer;
import gmdev.platform.logviewer.server.StateBuffer;
import gmdev.platform.logviewer.util.LogEntryStatus;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
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
    MetaDataHelper meta;

    @Autowired
    EntryProcessor processor;

    @Autowired
    Parser parser;

    @Autowired
    AlertManagerRepo repo;

    @Autowired
    StateBuffer state;

    @Override
    public void ingest() {
        log.info("AlertManager Ingester running");

        HttpClient http = new DefaultHttpClient();

        /*
        String user = env.getProperty("elastic.user");
        String password = env.getProperty("elastic.password");
        if (user != null && !user.isEmpty()) {
            CredentialsProvider provider = new BasicCredentialsProvider();
            UsernamePasswordCredentials creds = new UsernamePasswordCredentials(user, password);
            provider.setCredentials(AuthScope.ANY, creds);
            try {
                http = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();
            } catch(Exception e) {
                log.error("Unable to create http client with credentials");
            }
        }
        */

        //HttpGet get = new HttpGet(env.getProperty("elastic.url"));
        HttpGet get = new HttpGet(env.getProperty("alertmanager.url"));
        HttpGet get2 = new HttpGet(env.getProperty("alertmanager.silences.url"));
        //HttpGet get = new HttpGet("http://localhost:9093/api/v1/alerts");
        //post.addHeader("Content-Type", "application/json");
        try {
            HttpResponse response = http.execute(get);
            BufferedReader bR = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            String line = "";

            StringBuilder responseStrBuilder = new StringBuilder();
            while((line =  bR.readLine()) != null){
                responseStrBuilder.append(line);
            }
            JSONObject json = new JSONObject(responseStrBuilder.toString());
            //log.info(json.toString());;

            List<AlertManagerEntry> allActive = repo.findAll();

            //process the incoming active alerts
            JSONArray data = json.getJSONArray("data");
            for (int i = 0;i < data.length();i++) {
                String jsonAlert = data.getJSONObject(i).toString();
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                Alert alertFromAlertmanager = objectMapper.readValue(jsonAlert, Alert.class);
                Optional<AlertManagerEntry> alertFromDatabase = repo.findById(alertFromAlertmanager.getFingerprint());
                AlertManagerEntry databaseEntry;
                if (alertFromDatabase.isPresent()) {
                    databaseEntry = alertFromDatabase.get();
                    databaseEntry.setAlert(alertFromAlertmanager);
                    if (LogEntryStatus.RESOLVED.equals(databaseEntry.getStatus())) {
                        databaseEntry.addNote("System", "Previously RESOLVED alert is now NEW");
                        databaseEntry.setStatus(LogEntryStatus.NEW);
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

                    allActive.remove(databaseEntry);
                } else {
                    databaseEntry = new AlertManagerEntry(alertFromAlertmanager);
                }
                repo.save(databaseEntry);
            }

            //process existing alerts that are not existing
            for (AlertManagerEntry entry:allActive) {
                if (!LogEntryStatus.RESOLVED.equals(entry.getStatus())) {
                    entry.setStatus(LogEntryStatus.RESOLVED);
                    entry.addNote("System", "Alert is now RESOLVED");
                    repo.save(entry);
                }
            }

            //TODO timeout resolved alerts

            //silences

            HttpResponse response2 = http.execute(get2);
            BufferedReader bR2 = new BufferedReader(new InputStreamReader(response2.getEntity().getContent()));
            line = "";

            StringBuilder responseStrBuilder2 = new StringBuilder();
            while((line =  bR2.readLine()) != null){
                responseStrBuilder2.append(line);
            }
            JSONObject silencesJson = new JSONObject(responseStrBuilder2.toString());
            //log.debug("SILENCES JSON: "+silencesJson.toString());

            JSONArray silences = silencesJson.getJSONArray("data");
            List<Silence> existingSilences = new ArrayList<>();
            for (int i = 0;i < silences.length();i++) {
                String jsonSilence = silences.getJSONObject(i).toString();
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                final SimpleModule module = new SimpleModule("", Version.unknownVersion());
                module.addDeserializer(LocalDateTime.class, new CustomDateDeserializer());
                objectMapper.registerModule(module);
                Silence silence = objectMapper.readValue(jsonSilence, Silence.class);
                if (silence.getStatus().getState().equals("active")) {
                    //set hours based on dates
                    Duration dur = Duration.between(silence.getStartsat(), silence.getEndsat());
                    silence.setHours(dur.toHours());
                    Duration rem = Duration.between(LocalDateTime.now(), silence.getEndsat());
                    silence.setHoursLeft(rem.toHours());

                    existingSilences.add(silence);
                }
            }
            state.setSilences(existingSilences);
            log.debug("SILENCES ADDED: "+existingSilences.size());

        } catch(Exception e) {
            e.printStackTrace(System.out);
        }

    }
}
