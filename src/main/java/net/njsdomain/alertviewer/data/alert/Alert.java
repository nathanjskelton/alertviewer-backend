package net.njsdomain.alertviewer.data.alert;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import net.njsdomain.alertviewer.ingest.LocalDateTimeDeserializer;
import net.njsdomain.alertviewer.ingest.alertmananer.AlertIngester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Document(collection = "Alert")
public class Alert {
    private static final Logger log = LoggerFactory.getLogger(Alert.class);

    Map<String, String> labels;

    Map<String, String> annotations;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    LocalDateTime startsAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    LocalDateTime endsAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    LocalDateTime updatedAt = LocalDateTime.now(ZoneOffset.UTC);

    String generatorURL;

    AlertStatus status;

    Set<Object> receivers;

    String fingerprint;

    public Alert() {
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * The receivers alertmanager routed this alert to, as plain names.
     *
     * The raw field holds whatever the api version gave us: v2 sends objects
     * ({"name":"bugle"}) and v1 sends bare strings, so both shapes sit in the
     * database. Normalising once here keeps every consumer off that difference.
     * Spring Data maps fields rather than getters, so this adds nothing to mongo.
     */
    @JsonProperty("receiverNames")
    public List<String> getReceiverNames() {
        List<String> names = new ArrayList<>();
        if (receivers == null) {
            return names;
        }
        for (Object r : receivers) {
            String name = null;
            if (r instanceof Map) {
                Object n = ((Map<?, ?>) r).get("name");
                name = (n == null) ? null : String.valueOf(n);
            } else if (r != null) {
                name = String.valueOf(r);
            }
            if (name != null && !name.isBlank() && !names.contains(name)) {
                names.add(name);
            }
        }
        Collections.sort(names);
        return names;
    }

    @JsonProperty("labels")
    public Map<String, String> getLabels() {
        return labels;
    }

    @JsonProperty("labels")
    public void setLabels(Map<String, String> labels) {
        //an alert that names its environment under some other label still belongs to
        //that environment, so copy it across before anything else reads it
        String alternative = alternativeEnvironmentLabel;
        if (alternative != null && labels.get("environment") == null && labels.get(alternative) != null) {
            labels.put("environment", labels.get(alternative));
        }
        this.labels = labels;
    }

    /**
     * The label an alert may carry instead of "environment", from
     * environment.label.alternative.
     *
     * Static because alerts are deserialized by jackson rather than built by spring,
     * so there is nothing here to inject into. It keeps a working default so the class
     * behaves the same in a test with no spring context around it, and a blank setting
     * turns the fallback off for a site that does not need one.
     */
    private static volatile String alternativeEnvironmentLabel = "gm_instance";

    public static void setAlternativeEnvironmentLabel(String label) {
        alternativeEnvironmentLabel = (label == null || label.isBlank()) ? null : label.trim();
    }

    public static String getAlternativeEnvironmentLabel() {
        return alternativeEnvironmentLabel;
    }

    public Map<String, String> getAnnotations() {
        return annotations;
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

    public String getGeneratorURL() {
        return generatorURL;
    }

    public void setGeneratorURL(String generatorURL) {
        this.generatorURL = generatorURL;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    public Set<Object> getReceivers() {
        return receivers;
    }

    public void setReceivers(Set<Object> receivers) {
        this.receivers = receivers;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Alert alert = (Alert) o;
        return Objects.equals(fingerprint, alert.fingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fingerprint);
    }

    @Override
    public String toString() {
        return "Alert{" +
                "labels=" + labels +
                ", annotations=" + annotations +
                ", startsAt='" + startsAt + '\'' +
                ", endsAt='" + endsAt + '\'' +
                ", generatorURL='" + generatorURL + '\'' +
                ", status=" + status +
                ", receivers=" + receivers +
                ", fingerprint='" + fingerprint + '\'' +
                '}';
    }
}
