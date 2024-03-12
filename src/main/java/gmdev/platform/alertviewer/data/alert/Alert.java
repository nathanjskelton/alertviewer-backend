package gmdev.platform.alertviewer.data.alert;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import gmdev.platform.alertviewer.ingest.LocalDateTimeDeserializer;
import gmdev.platform.alertviewer.ingest.alertmananer.AlertIngester;
import org.apache.commons.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.web.bind.annotation.PostMapping;

import javax.annotation.PostConstruct;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Document(collection = "Alert")
public class Alert implements Comparable<Alert> {
    private static final Logger log = LoggerFactory.getLogger(Log.class);

    static MessageDigest digester;

    static {
        try {
            digester = MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            throw new RuntimeException("*** FATAL ERROR CONSTRUCTING DIGESTER, CANNOT CREATE ALERT ***");
        }
    }

    private static final Object MUTEX = new Object();


    @Id
    String id;


    Map<String, String> labels;

    @JsonProperty
    Map<String, String> annotations = new HashMap<>();

    String annotationHash;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    LocalDateTime startsAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    LocalDateTime endsAt;

    String generatorURL;

    AlertStatus status;

    boolean instanceInferred = false;

    String alertmanager;

    Set<String> receivers;

    String fingerprint;

    LocalDateTime ingestTime;

    String friendlyIngestTime;

    public Alert() {
        this.id = UUID.randomUUID().toString();
    }
    

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public Iterator<String> getAnnotationKeys() {
        synchronized (MUTEX) {
            return this.annotations.keySet().iterator();
        }
    }

    public void setAlertmanager(String alertmanager) {
        this.alertmanager = alertmanager;
    }

    public String getAlertmanager() {
        return alertmanager;
    }

    public String getAnnotation(String key) {
        synchronized (MUTEX) {
            return this.annotations.get(key);
        }
    }


    public void setFriendlyIngestTime(String ingestTime) {
        this.friendlyIngestTime = ingestTime;
    }


    public String getFriendlyIngestTime() {
        return friendlyIngestTime;
    }

    public void setIngestTime(LocalDateTime ingestTime) {
        this.ingestTime = ingestTime;
    }


    public LocalDateTime getIngestTime() {
        return ingestTime;
    }

    public boolean isInstanceInferred() {
        return instanceInferred;
    }

    public void setInstanceInferred(boolean instanceInferred) {
        this.instanceInferred = instanceInferred;
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

    public Set<String> getReceivers() {
        return receivers;
    }

    public void setReceivers(Set<String> receivers) {
        this.receivers = receivers;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getAnnotationHash() {
        log.debug("getAnnotationHash returning "+ annotationHash);
        return annotationHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Alert alert = (Alert) o;
        return Objects.equals(annotationHash, alert.annotationHash) && Objects.equals(fingerprint, alert.fingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(annotationHash, fingerprint);
    }

    public void calculateAnnotationHash() {
        synchronized (MUTEX) {
            log.debug("calculateAnnotationHash");
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : annotations.entrySet()) {
                sb.append(entry.getKey());
                sb.append(entry.getValue());
            }
            annotationHash = new String(digester.digest(sb.toString().getBytes()));
        }
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


    @Override
    public int compareTo(Alert alert) {
        return ingestTime.compareTo(alert.ingestTime);
    }
}
