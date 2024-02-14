package gmdev.platform.alertviewer.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PollResult {

    private List<Alert> messageStack;
    private String statusMessage;
    private String sessionId;
    private boolean dataStale;

    private Map<String, Boolean> alertManagerStatus = new HashMap<>();

    private boolean locked;

    public PollResult(String sessionId, List<Alert> messageStack, String statusMessage, boolean dataStale,
                      Set<String> alertManagersAll, Set<String> alertManagersUp) {
        this.messageStack = messageStack;
        this.statusMessage = statusMessage;
        this.dataStale = dataStale;
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


    public boolean isDataStale() {
        return dataStale;
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
