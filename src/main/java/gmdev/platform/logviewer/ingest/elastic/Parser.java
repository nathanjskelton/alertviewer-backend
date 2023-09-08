package gmdev.platform.logviewer.ingest.elastic;

import gmdev.platform.logviewer.ingest.IngestedEntry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;

@Component
public class Parser {
    private static final Logger log = LoggerFactory.getLogger(Parser.class);

    @Autowired
    Environment env;

    public List<IngestedEntry> parse(InputStream is, String timeFormat) {
        List<IngestedEntry> list = new ArrayList<>();

        try {
            StringBuilder sb = new StringBuilder();
            InputStreamReader reader = new InputStreamReader(is);
            while (reader.ready()) {
                sb.append((char)reader.read());
            }
            reader.close();

            JSONObject jo = new JSONObject(sb.toString());
            log.info(jo.getJSONObject("hits").toString());

            if (jo.getJSONObject("hits") != null) {
                JSONArray hits = jo.getJSONObject("hits").getJSONArray("hits");
                for (int i = 0;i < hits.length(); i++) {
                    JSONObject hit = hits.getJSONObject(i);

                    String msg = hit.getJSONObject("_source").getString(env.getProperty("elastic.entry.msgfield"));
                    String rawtime = hit.getJSONObject("_source").getString(env.getProperty("elastic.timestamp.field"));
                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern(timeFormat);
                    TemporalAccessor ta = dtf.parse(rawtime);
                    LocalDateTime timestamp = LocalDateTime.from(ta);

                    String logtype = hit.getJSONObject("_source").getString("priority");
                    String id = hit.getString("_id");

                    IngestedEntry ie = new IngestedEntry(timestamp, logtype, id, msg);
                    list.add(ie);
                }
            }
        } catch(Exception e) {
            log.error("Unable to parse json from elastic",e);
        }
        return list;
    }
}
