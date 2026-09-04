package net.njsdomain.alertviewer.ingest.alertmananer;

import net.njsdomain.alertviewer.data.AlertManagerConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AlertManagerUtil {
    private static final Logger log = LoggerFactory.getLogger(AlertManagerUtil.class);

    public JSONArray getJsonArray(AlertManagerConfig amConfig, String jsonText) {
        JSONArray json;
        log.info(amConfig.toString());
        if ("v2".equals(amConfig.getApiVersion())) {
            json = new JSONArray(jsonText);
            log.info("API v2 configured");
        } else {
            JSONObject jsonObject = new JSONObject(jsonText);
            json = jsonObject.getJSONArray("data");
            log.info("API v1 configured");
        }
        return json;
    }

}
