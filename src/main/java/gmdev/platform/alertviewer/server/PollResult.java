package gmdev.platform.alertviewer.server;

import java.util.List;

public class PollResult {

    private List<Alert> messageStack;
    private String statusMessage;
    private String sessionId;
    private boolean dataStale;
    private boolean locked;

    public PollResult(String sessionId, List<Alert> messageStack, String statusMessage, boolean dataStale, boolean locked) {
        this.messageStack = messageStack;
        this.statusMessage = statusMessage;
        this.dataStale = dataStale;
        this.locked = locked;
        this.sessionId = sessionId;
    }

    public List<Alert> getMessageStack() {
        return messageStack;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public boolean isDataStale() {
        return dataStale;
    }

    public boolean isLocked() {
        return locked;
    }

    public String getSessionId() {
        return sessionId;
    }
}
