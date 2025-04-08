package gmdev.platform.alertviewer.ingest.alertmananer;

import gmdev.platform.alertviewer.data.AlertManagerConfig;
import gmdev.platform.alertviewer.server.StateBuffer;
import gmdev.platform.alertviewer.util.SSLContextFactory;
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
        JSONArray json = null;

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
            } catch (HttpTimeoutException te) {
                log.warn("Request timed out while connected to "+amConfig.getName()+" during attempt "+tries);
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
            log.error("Response: "+response.body());
            state.getAlertManagersUp().remove(amConfig.getName());
            return null;
        }

        try {
            log.debug("RESPONSE: "+response.body());
            if ("v2".equals(amConfig.getApiVersion())) {
                json = new JSONArray(response.body());
                log.info("API v2 configured");
            } else {
                JSONObject jsonObject = new JSONObject(response.body());
                json = jsonObject.getJSONArray("data");
                log.info("API v1 configured");
            }
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

    public JSONArray getSilences(StateBuffer state, AlertManagerConfig amConfig) throws Exception {
        java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder().sslContext(sslContextFactory.getSSLContext()).build();
        HttpRequest request = HttpRequest.newBuilder().timeout(Duration.ofSeconds(10))
                .uri(new URI(amConfig.getSilencesUrl()))
                .GET()
                .build();

        return sendRequest(state, amConfig, request);
    }
}
