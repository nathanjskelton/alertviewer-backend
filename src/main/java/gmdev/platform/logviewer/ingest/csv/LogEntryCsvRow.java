package gmdev.platform.logviewer.ingest.csv;

import com.opencsv.bean.CsvBindAndJoinByPosition;
import com.opencsv.bean.CsvBindByPosition;
import org.apache.commons.collections4.MultiValuedMap;

import java.time.LocalDateTime;


public class LogEntryCsvRow {

    @CsvBindByPosition(position = 0)
    private String timestamp;

    @CsvBindByPosition(position = 1)
    private String id;

    @CsvBindByPosition(position = 2)
    private String logType;

    @CsvBindAndJoinByPosition(position = "3-", elementType = String.class)
    private MultiValuedMap<String, String> messageParts;


    public LogEntryCsvRow() { }

    public LocalDateTime getTimestamp() {
        if (timestamp.length() > 19) {
            timestamp = timestamp.substring(0,20);
        }
        timestamp = timestamp.replace(" ", "T");
        return LocalDateTime.parse(timestamp);
    }

    public String getId() {
        return id;
    }

    public String getLogType() {
        return logType;
    }

    public String getMessage() {
        if (messageParts == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String s:messageParts.values()) {
            sb.append(s);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "LogEntryCsvRow{" +
                "timestamp=" + timestamp +
                ", id='" + id + '\'' +
                ", logType='" + logType + '\'' +
                ", messageParts=" + messageParts +
                '}';
    }
}
