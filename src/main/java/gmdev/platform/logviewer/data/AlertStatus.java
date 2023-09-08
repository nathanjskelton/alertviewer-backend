package gmdev.platform.logviewer.data;

public class AlertStatus {

    String state;
    String silencedBy;
    String inhibitedBy;

    public AlertStatus() {
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getSilencedBy() {
        return silencedBy;
    }

    public void setSilencedBy(String silencedBy) {
        this.silencedBy = silencedBy;
    }

    public String getInhibitedBy() {
        return inhibitedBy;
    }

    public void setInhibitedBy(String inhibitedBy) {
        this.inhibitedBy = inhibitedBy;
    }
}
