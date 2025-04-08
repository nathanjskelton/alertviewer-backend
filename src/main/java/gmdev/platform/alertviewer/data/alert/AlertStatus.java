package gmdev.platform.alertviewer.data.alert;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AlertStatus {

    @JsonProperty("inhibitedBy")
    private List<String> inhibitedby;
    @JsonProperty("silencedBy")
    private List<String> silencedby;
    @JsonProperty("mutedBy")
    private List<String> mutedBy;
    private String state;
    public void setInhibitedby(List<String> inhibitedby) {
        this.inhibitedby = inhibitedby;
    }
    public List<String> getInhibitedby() {
        return inhibitedby;
    }

    public void setSilencedby(List<String> silencedby) {
        this.silencedby = silencedby;
    }
    public List<String> getSilencedby() {
        return silencedby;
    }

    public void setState(String state) {
        this.state = state;
    }
    public String getState() {
        return state;
    }

    public List<String> getMutedBy() {
        return mutedBy;
    }

    public void setMutedBy(List<String> mutedBy) {
        this.mutedBy = mutedBy;
    }
}