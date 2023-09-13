package gmdev.platform.logviewer.ingest.alertmananer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import gmdev.platform.logviewer.data.Alert;
import gmdev.platform.logviewer.data.AlertManagerEntry;
import gmdev.platform.logviewer.data.AlertManagerRepo;
import gmdev.platform.logviewer.data.MetaDataHelper;
import gmdev.platform.logviewer.ingest.EntryProcessor;
import gmdev.platform.logviewer.ingest.IngestedEntry;
import gmdev.platform.logviewer.ingest.Ingester;
import gmdev.platform.logviewer.ingest.elastic.Parser;
import gmdev.platform.logviewer.util.LogEntryStatus;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.time.*;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        HttpGet get = new HttpGet("http://localhost:9093/api/v1/alerts");
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
                Alert alert = objectMapper.readValue(jsonAlert, Alert.class);
                Optional<AlertManagerEntry> entryOpt = repo.findById(alert.getFingerprint());
                AlertManagerEntry entry;
                if (entryOpt.isPresent()) {
                    entry = entryOpt.get();
                    entry.setAlert(alert);
                    if (LogEntryStatus.RESOLVED.equals(entry.getStatus())) {
                        entry.addNote("System", "Previously RESOLVED alert is now NEW");
                        entry.setStatus(LogEntryStatus.NEW);
                    }
                    allActive.remove(entry);
                } else {
                    entry = new AlertManagerEntry(alert);
                }
                repo.save(entry);

                log.info(entry.toString());
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

        } catch(Exception e) {
            e.printStackTrace(System.out);
        }

    }
}
