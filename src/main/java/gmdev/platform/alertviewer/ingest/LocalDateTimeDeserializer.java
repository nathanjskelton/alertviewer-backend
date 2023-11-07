package gmdev.platform.alertviewer.ingest;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {

    public LocalDateTimeDeserializer() {
        this(null);
    }

    public LocalDateTimeDeserializer(final Class<?> vc) {
        super(vc);
    }

    @Override
    public LocalDateTime deserialize(final JsonParser jsonParser, final DeserializationContext context) {
        try {
            DateTimeFormatter dtfx = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
            String s = jsonParser.getText();
            String x = "";
            if (s.endsWith("Z")) x = "Z";
            if (s.contains(".")) s = s.split("\\.")[0] + x;

            //return Currency.getInstance(jsonParser.getText());
            if (s.endsWith("Z")) {
                return LocalDateTime.from(dtfx.parse(s));
            }
            return LocalDateTime.from(dtf.parse(s));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}