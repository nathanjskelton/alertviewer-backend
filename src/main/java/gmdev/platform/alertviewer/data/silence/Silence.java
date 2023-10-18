package gmdev.platform.alertviewer.data.silence;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;


public class Silence {

    private static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    private List<Matchers> matchers;
    @JsonProperty("createdBy")
    private String createdby;


    private String alertmanager;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_PATTERN)
    @JsonProperty("startsAt")
    private LocalDateTime startsat;
    private String comment;
    private String id;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_PATTERN)
    @JsonProperty("endsAt")
    private LocalDateTime endsat;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_PATTERN)
    @JsonProperty("updatedAt")
    private LocalDateTime updatedat;
    @JsonProperty("hours")
    private long hours;

    @JsonProperty("hoursLeft")
    private long hoursLeft;

    private Status status;
    public void setMatchers(List<Matchers> matchers) {
        this.matchers = matchers;
    }
    public List<Matchers> getMatchers() {
        return matchers;
    }

    public void setCreatedby(String createdby) {
        this.createdby = createdby;
    }
    public String getCreatedby() {
        return createdby;
    }

    public void setStartsat(LocalDateTime startsat) {
        this.startsat = startsat;
    }
    public LocalDateTime getStartsat() {
        return startsat;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
    public String getComment() {
        return comment;
    }

    public void setId(String id) {
        this.id = id;
    }
    public String getId() {
        return id;
    }

    public void setEndsat(LocalDateTime endsat) {
        this.endsat = endsat;
    }
    public LocalDateTime getEndsat() {
        return endsat;
    }

    public void setUpdatedat(LocalDateTime updatedat) {
        this.updatedat = updatedat;
    }
    public LocalDateTime getUpdatedat() {
        return updatedat;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
    public Status getStatus() {
        return status;
    }

    public long getHours() {
        return hours;
    }

    public void setHours(long hours) {
        this.hours = hours;
    }

    public long getHoursLeft() {
        return hoursLeft;
    }

    public void setHoursLeft(long hoursLeft) {
        this.hoursLeft = hoursLeft;
    }


    public String getAlertmanager() {
        return alertmanager;
    }

    public void setAlertmanager(String alertmanager) {
        this.alertmanager = alertmanager;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Silence silence = (Silence) o;
        return Objects.equals(alertmanager, silence.alertmanager) && Objects.equals(id, silence.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alertmanager, id);
    }
}