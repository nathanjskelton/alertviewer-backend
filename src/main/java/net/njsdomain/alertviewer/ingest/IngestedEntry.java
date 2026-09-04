package net.njsdomain.alertviewer.ingest;

import java.time.LocalDateTime;

public class IngestedEntry {
    LocalDateTime timestamp;
    String logType;
    String entryId;
    String message;

    public IngestedEntry(LocalDateTime timestamp, String logType, String entryId, String message) {
        this.timestamp = timestamp;
        this.logType = logType;
        this.entryId = entryId;
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getLogType() {
        return logType;
    }

    public String getEntryId() {
        return entryId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }


    @Override
    public String toString() {
        return "IngestedEntry{" +
                "timestamp=" + timestamp +
                ", logType='" + logType + '\'' +
                ", elasticId='" + entryId + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
