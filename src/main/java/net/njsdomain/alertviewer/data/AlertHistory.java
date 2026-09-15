package net.njsdomain.alertviewer.data;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import net.njsdomain.alertviewer.util.LogEntryStatus;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

/**
 * The slice of a resolved alert that the timeline graph and the gantt actually
 * read. Returned instead of the full AlertManagerEntry when the caller did not
 * ask for RESOLVED alerts: they are needed to draw the history, but never to
 * fill a table row, so the notes, receivers, generator URL, friendly timestamps
 * and every unreferenced label can stay in mongo.
 *
 * The JSON shape deliberately mirrors AlertManagerEntry -- same nesting, same
 * date format -- so the client can walk these and real entries with one code
 * path rather than special-casing them.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlertHistory {

    private static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    @Id
    private String id;

    private LogEntryStatus status;

    private HistoryAlert alert;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LogEntryStatus getStatus() {
        return status;
    }

    public void setStatus(LogEntryStatus status) {
        this.status = status;
    }

    public HistoryAlert getAlert() {
        return alert;
    }

    public void setAlert(HistoryAlert alert) {
        this.alert = alert;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HistoryAlert {

        /** Only alertname, severity, environment, instance and team are projected. */
        private Map<String, String> labels;

        /** Only summary is projected. */
        private Map<String, String> annotations;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_PATTERN)
        private LocalDateTime startsAt;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_PATTERN)
        private LocalDateTime endsAt;

        /** Never null: an alert missing every projected label still has to be
         *  walkable by the client without a guard on each access. */
        public Map<String, String> getLabels() {
            return labels == null ? Collections.emptyMap() : labels;
        }

        public void setLabels(Map<String, String> labels) {
            this.labels = labels;
        }

        /** Never null, for the same reason as {@link #getLabels()} -- an alert
         *  carrying no summary annotation is perfectly legal. */
        public Map<String, String> getAnnotations() {
            return annotations == null ? Collections.emptyMap() : annotations;
        }

        public void setAnnotations(Map<String, String> annotations) {
            this.annotations = annotations;
        }

        public LocalDateTime getStartsAt() {
            return startsAt;
        }

        public void setStartsAt(LocalDateTime startsAt) {
            this.startsAt = startsAt;
        }

        public LocalDateTime getEndsAt() {
            return endsAt;
        }

        public void setEndsAt(LocalDateTime endsAt) {
            this.endsAt = endsAt;
        }
    }
}
