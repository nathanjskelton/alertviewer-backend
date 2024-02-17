package gmdev.platform.alertviewer.data;

import gmdev.platform.alertviewer.server.RestEndpoint;
import gmdev.platform.alertviewer.util.LogEntryStatus;
import gmdev.platform.alertviewer.data.alert.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Document(collection = "AlertManagerEntry")
public class AlertManagerEntry {
    private static final Logger log = LoggerFactory.getLogger(AlertManagerEntry.class);

    @Id
    private String id;


    private String alertmanager;


    private List<Note> notes;
    @Indexed private LogEntryStatus status;

    private boolean regex = false;

    private boolean acked = false;

    private boolean flapping = false;

    private String friendlyStartTime;

    private String friendlyEndTime;

    private Alert alert;

    Set<String> fieldsToAggregate;

    Set<String> fingerprints = new HashSet<>();


    public AlertManagerEntry(String id, Alert alert, Set<String> fieldsToAggregate) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy.MM.dd 'at' HH:mm:ss");
        friendlyStartTime = alert.getStartsAt().format(dtf);
        friendlyEndTime = alert.getEndsAt().format(dtf);

        this.fieldsToAggregate = fieldsToAggregate;
        this.id = id;
        this.status = LogEntryStatus.NEW;
        notes = new ArrayList<>();
        setAlert(alert);
    }

    public String getId() {
        return id;
    }

    public Alert getAlert() {
        return alert;
    }

    public boolean isGroup() {
        return !alert.getFingerprint().equals(id);
    }

    public Set<String> getFieldsToAggregate() {
        return fieldsToAggregate;
    }

    public Set<String> getFingerprints() {
        return fingerprints;
    }

    public String getFriendlyStartTime() {
        return friendlyStartTime;
    }

    public String getFriendlyEndTime() {
        return friendlyEndTime;
    }

    public void setAlert(Alert alert) {
        if (this.fingerprints.contains(alert.getFingerprint())) {
            //update it
            this.alert.setEndsAt(alert.getEndsAt());
            this.alert.setStartsAt(alert.getStartsAt());
            this.alert.setReceivers(alert.getReceivers());
            this.alert.setGeneratorURL(alert.getGeneratorURL());
        } else {
            //add it
            if (this.alert != null && isGroup()) {
                //log.debug("Aggregating alert: ");
                Map<String, String> newAnnotations = new HashMap<>();
                if (this.alert.getAnnotations() != null) {
                    newAnnotations.putAll(this.alert.getAnnotations());
                }
                for (String f : fieldsToAggregate) {
                    //log.debug("^ Aggregating field: " + f);
                    String a = this.alert.getAnnotations().get(f);
                    //log.debug("^ ..was: " + a);
                    String na = alert.getAnnotations().get(f);
                    if (na != null && !na.isEmpty()) {
                        a = a + "\n" + na;
                    }
                    //log.debug("^ ...is: " + a);
                    newAnnotations.put(f, a);
                }
                alert.setAnnotations(newAnnotations);
            }
            this.alert = alert;
            this.fingerprints.add(this.alert.getFingerprint());
        }
    }

    public LogEntryStatus getStatus() {
        return status;
    }

    public void setStatus(LogEntryStatus status) {
        this.status = status;
    }

    public boolean isAcked() {
        return acked;
    }

    public void setAcked(boolean acked) {
        this.acked = acked;
    }

    public boolean isFlapping() {
        return flapping;
    }

    public void setFlapping(boolean flapping) {
        this.flapping = flapping;
    }

    public List<Note> getNotes() {
        Collections.sort(notes);
        return notes;
    }

    public void setNotes(List<Note> notes) {
        this.notes = notes;
    }

    public void addNote(String user, String message) {
        Note note = new Note(LocalDateTime.now(), user, message);
        notes.add(note);
    }

    public boolean isRegex() {
        return regex;
    }

    public void setRegex(boolean regex) {
        this.regex = regex;
    }


    public String getDuration() {

        Duration duration = Duration.between(alert.getStartsAt(), alert.getEndsAt());

        long DD = duration.toDays();
        long HH = duration.toHoursPart();
        int MM = duration.toMinutesPart();
        int SS = duration.toSecondsPart();
        String time;
        if (DD == 0 && HH == 0) {
            time = String.format("%01dm", MM);
        } else if (DD == 0) {
            time = String.format("%01dh %01dm", HH, MM);
        } else {
            time = String.format("%01dd %01dh %01dm", DD, HH, MM);
        }
        return time;
    }

    public String getAlertmanager() {
        return alertmanager;
    }

    public void setAlertmanager(String alertmanager) {
        this.alertmanager = alertmanager;
        String gm = alert.getLabels().get("gm_instance");
        if (gm == null || gm.isEmpty()) {
            gm = alertmanager;
            alert.getLabels().put("gm_instance", gm);
            alert.getAnnotations().put("gm_instance_from_am", "true");
        }
    }

    @Override
    public String toString() {
        return "Alert{" +
                "id='" + id + '\'' +
                ", status=" + status +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlertManagerEntry that = (AlertManagerEntry) o;
        return Objects.equals(alertmanager, that.alertmanager) && Objects.equals(alert, that.alert);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alertmanager, alert);
    }
}
