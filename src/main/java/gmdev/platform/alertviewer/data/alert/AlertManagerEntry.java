package gmdev.platform.alertviewer.data.alert;

import gmdev.platform.alertviewer.data.Note;
import gmdev.platform.alertviewer.util.LogEntryStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Document(collection = "AlertManagerEntry")
public class AlertManagerEntry {

    @Id
    private String id;

    private String alertmanager = null;

    private List<Note> notes;
    @Indexed private LogEntryStatus status;

    private boolean acked = false;

    private boolean flapping = false;

    private LocalDateTime start = LocalDateTime.now();

    private LocalDateTime end = LocalDateTime.now().plusMinutes(5);

    static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy.MM.dd 'at' HH:mm:ss");

    @DBRef
    private Set<Alert> alerts = new TreeSet<>();

    private AlertManagerEntry() {
    }
 
    public AlertManagerEntry(Alert alert, String alertmanager) {
        this.id = alert.getFingerprint();
        addAlert(alert, alertmanager);
        this.status = LogEntryStatus.NEW;
        notes = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    //get the first alert which has the labels and values but no annotations
    public Alert getAlert() {
        Optional<Alert> a = alerts.stream().findFirst();
        return a.orElse(null);
    }

    public Collection<Alert> getAlerts() {
        return alerts;
    }

    public String getFriendlyStartTime() {
        return dtf.format(start);
    }

    public String getFriendlyEndTime() {
        return dtf.format(end);
    }

    public void addAlert(Alert alert, String alertmanager) {
        if (!alert.getFingerprint().equals(this.id) || (this.alertmanager != null && !this.alertmanager.equals(alertmanager))) {
            throw new RuntimeException("Fingerprint or Alertmanager mismatch: "+this.id+" != "+alert.getFingerprint());
        }

        this.alertmanager = alertmanager;
        String gm = alert.getLabels().get("gm_instance");
        if (gm == null || gm.isEmpty()) {
            gm = alertmanager;
            alert.getLabels().put("gm_instance", gm);
            alert.setInstanceInferred(true);
        }

        if (alert.getStartsAt().isBefore(start)) {
            start = alert.getStartsAt();
        }
        if (alert.getEndsAt().isAfter(end)) {
            end = alert.getEndsAt();
        }
        this.alerts.add(alert);
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



    public String getDuration() {

        Duration duration = Duration.between(start, end);

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
        return Objects.equals(id, that.id) && Objects.equals(alertmanager, that.alertmanager);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, alertmanager);
    }
}
