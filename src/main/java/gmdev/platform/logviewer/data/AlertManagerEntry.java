package gmdev.platform.logviewer.data;

import gmdev.platform.logviewer.util.LogEntryStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Document(collection = "AlertManagerEntry")
public class AlertManagerEntry {

    @Id
    private String id;

    private List<Note> notes;
    @Indexed private LogEntryStatus status;

    private boolean regex = false;

    private String friendlyStartTime;

    private String friendlyEndTime;

    private Alert alert;

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
                this.id+">>>\n"+this.alert.toString()+"\n\n"+alert.fingerprint+">>>\n"+alert.toString());}
        this.alert = alert;
    }

    public LogEntryStatus getStatus() {
        return status;
    }

    public void setStatus(LogEntryStatus status) {
        this.status = status;
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

    @Override
    public String toString() {
        return "LogEntry{" +
                "id='" + id + '\'' +
                ", status=" + status +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlertManagerEntry entry = (AlertManagerEntry) o;
        return Objects.equals(alert, entry.alert);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alert);
    }
}
