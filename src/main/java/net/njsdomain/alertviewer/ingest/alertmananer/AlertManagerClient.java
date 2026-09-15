package net.njsdomain.alertviewer.ingest.alertmananer;

import net.njsdomain.alertviewer.data.AlertManagerConfig;
import net.njsdomain.alertviewer.server.StateBuffer;
import net.njsdomain.alertviewer.util.SSLContextFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

@Component
public class AlertManagerClient {
    private static final Logger log = LoggerFactory.getLogger(AlertManagerClient.class);

    @Autowired
    SSLContextFactory sslContextFactory;

    public JSONArray sendRequest(StateBuffer state, AlertManagerConfig amConfig, HttpRequest request) {
        String body = fetchBody(state, amConfig, request, true, 5);
        if (body == null) {
            return null;
        }
        try {
            log.debug("RESPONSE: "+body);
            if ("v2".equals(amConfig.getApiVersion())) {
                log.info("API v2 configured");
                return new JSONArray(body);
            }
            log.info("API v1 configured");
            return new JSONObject(body).getJSONArray("data");
        } catch(Exception e) {
            log.error("Unable to parse JSON during ingest ("+amConfig.getName()+"): " + body, e);
            state.getAlertManagersUp().remove(amConfig.getName());
            return null;
        }
    }

    //shared transport for every GET: ssl context, the 5-try retry and the status check.
    //affectsHealth is false for lookups that must not mark an alertmanager down -- failing
    //to read the routing config says nothing about whether alerts are ingesting. maxTries
    //keeps a side lookup from spending the whole ingest cycle retrying a sick alertmanager.
    private String fetchBody(StateBuffer state, AlertManagerConfig amConfig, HttpRequest request, boolean affectsHealth, int maxTries) {
        java.net.http.HttpClient http;
        try {
            http = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).sslContext(sslContextFactory.getSSLContext()).build();
        } catch(Exception e) {
            log.error("Unable to get HTTP connection during ingest ("+amConfig.getName()+")", e);
            if (affectsHealth) state.getAlertManagersUp().remove(amConfig.getName());
            return null;
        }

        java.net.http.HttpResponse<String> response = null;
        int tries = 0;
        boolean success = false;
        while (tries < maxTries && !success) {
            tries++;
            try {
                response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                success = true;
            } catch (HttpConnectTimeoutException toe) {
                log.warn("Connection timed out connecting to "+amConfig.getName()+" during attempt "+tries);
            } catch (HttpTimeoutException te) {
                log.warn("Request timed out while connected to "+amConfig.getName()+" during attempt "+tries);
            } catch (Exception e) {
                log.error("Fatal error connecting to " + request.uri() + " during ingest (" + amConfig.getName() + ")", e);
                if (affectsHealth) state.getAlertManagersUp().remove(amConfig.getName());
                return null;
            }
        }
        if (!success) {
            log.error("Exhausted all attempts to connect to "+amConfig.getName());
            if (affectsHealth) state.getAlertManagersUp().remove(amConfig.getName());
            return null;
        }

        if (response.statusCode() != 200) {
            log.error("Response code != 200 connecting to " + request.uri() + " during ingest (" + amConfig.getName() + ")");
            log.error("Response: "+response.body());
            if (affectsHealth) state.getAlertManagersUp().remove(amConfig.getName());
            return null;
        }

        return response.body();
    }

    //Alertmanager publishes its routing tree only as the raw config yaml. v2 carries it at
    //config.original and answers even on instances configured here as v1, so it is tried
    //first; v1's data.configYAML is the fallback. Returns null if neither can be read.
    public String getRoutingYaml(StateBuffer state, AlertManagerConfig amConfig) {
        String yaml = readStatusYaml(state, amConfig, amConfig.getStatusUrl(), true);
        if (yaml == null) {
            log.debug("Falling back to the v1 status api for routing config ("+amConfig.getName()+")");
            yaml = readStatusYaml(state, amConfig, amConfig.getStatusUrlV1(), false);
        }
        return yaml;
    }

    private String readStatusYaml(StateBuffer state, AlertManagerConfig amConfig, String url, boolean v2) {
        try {
            HttpRequest request = HttpRequest.newBuilder().timeout(Duration.ofSeconds(10))
                    .uri(new URI(url))
                    .GET()
                    .build();
            //one attempt only: this is a side lookup, not the ingest itself
            String body = fetchBody(state, amConfig, request, false, 1);
            if (body == null) {
                return null;
            }
            JSONObject json = new JSONObject(body);
            String yaml = v2 ? json.getJSONObject("config").optString("original", null)
                             : json.getJSONObject("data").optString("configYAML", null);
            return (yaml == null || yaml.isBlank()) ? null : yaml;
        } catch (Exception e) {
            log.debug("Could not read routing config from "+url+" ("+amConfig.getName()+"): "+e.getMessage());
            return null;
        }
    }

    public JSONArray getSilences(StateBuffer state, AlertManagerConfig amConfig) throws Exception {
        java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder().sslContext(sslContextFactory.getSSLContext()).build();
        HttpRequest request = HttpRequest.newBuilder().timeout(Duration.ofSeconds(10))
                .uri(new URI(amConfig.getSilencesUrl()))
                .GET()
                .build();

        return sendRequest(state, amConfig, request);
    }
}
