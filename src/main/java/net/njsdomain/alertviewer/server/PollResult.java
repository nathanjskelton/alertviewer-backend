package net.njsdomain.alertviewer.server;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class PollResult {
    private List<Alert> messageStack;
    private String statusMessage;
    private String sessionId;
    private Instant lastIngestSuccess;
    private long lastIngestSecs;
    private String version;

    private Map<String, Boolean> alertManagerStatus = new TreeMap<>();

    private boolean locked;

    public PollResult(String sessionId, List<Alert> messageStack, String statusMessage, Instant lastIngestSuccess,
                      String version, Set<String> alertManagersAll, Set<String> alertManagersUp) {
        this.messageStack = messageStack;
        this.statusMessage = statusMessage;
        this.lastIngestSuccess = lastIngestSuccess;
        this.version = version;

        this.lastIngestSecs = ChronoUnit.SECONDS.between(lastIngestSuccess, Instant.now());
        for (String s:alertManagersAll) {
            Boolean b = Boolean.FALSE;
            if (alertManagersUp.contains(s)) b = Boolean.TRUE;
            alertManagerStatus.put(s, b);
        }
        this.sessionId = sessionId;
    }

    public List<Alert> getMessageStack() {
        return messageStack;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public  String getVersion() {
        return version;
    }

    public Instant getLastIngestSuccess() {
        return lastIngestSuccess;
    }

    public long getLastIngestSecs() {
        return lastIngestSecs;
    }

    public boolean isLocked() {
        return locked;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Map<String, Boolean> getAlertManagerStatus() {
        return alertManagerStatus;
    }
}
