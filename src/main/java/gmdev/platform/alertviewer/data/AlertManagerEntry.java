package gmdev.platform.alertviewer.data;

import gmdev.platform.alertviewer.util.LogEntryStatus;
import gmdev.platform.alertviewer.data.alert.Alert;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Document(collection = "AlertManagerEntry")
public class AlertManagerEntry {

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

    private long lastChange = 0L;

    public AlertManagerEntry(Alert alert) {
        this.alert = alert;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy.MM.dd 'at' HH:mm:ss");
        friendlyStartTime = alert.getStartsAt().format(dtf);
        friendlyEndTime = alert.getEndsAt().format(dtf);

        this.id = alert.getFingerprint();
        this.status = LogEntryStatus.NEW;
        notes = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public Alert getAlert() {
        return alert;
    }

    public String getFriendlyStartTime() {
        return friendlyStartTime;
    }

    public String getFriendlyEndTime() {
        return friendlyEndTime;
    }

    public void setAlert(Alert alert) {
        if (!alert.getFingerprint().equals(this.id)) {throw new RuntimeException("Fingerprint mismatch:\n"+
                this.id+">>>\n"+this.alert.toString()+"\n\n"+alert.getFingerprint()+">>>\n"+alert.toString());}
        this.alert = alert;
    }

    public long getLastChange() {
        return lastChange;
    }

    public void setLastChange(long lastChange) {
        this.lastChange = lastChange;
    }

    public LogEntryStatus getStatus() {
        return status;
    }

    public void setStatus(LogEntryStatus status) {
        lastChange = System.currentTimeMillis();
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
