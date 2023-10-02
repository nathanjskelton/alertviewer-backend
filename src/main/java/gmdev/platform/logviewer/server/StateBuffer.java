package gmdev.platform.logviewer.server;

import gmdev.platform.logviewer.data.silence.Silence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
public class StateBuffer {
    private static final Logger log = LoggerFactory.getLogger(StateBuffer.class);

    private Object MUTEX = new Object();
    private Long lock = 0L;
    private Map<String, CompletableFuture<String>> asyncs = new HashMap<>();
    private List<String> jobsInProgress = new ArrayList<>();
    private Map<String, List<Alert>> messageStack = new HashMap<>();
    private Map<String, Boolean> stale = new HashMap<>();
    private Map<String, Long> sessions = new HashMap<>();

    private final Set<Silence> silences = new HashSet<>();

    public boolean aquireLock() {
        synchronized (lock) {
            if (lock > 0) {
                return false;
            }
            lock = System.currentTimeMillis();
            return true;
        }
    }

    public void releaseLock() {
        synchronized (lock) {
            lock = 0L;
        }

    }


    public void addAsync(String name, CompletableFuture<String> future) {
        asyncs.put(name, future);
    }

    @Scheduled(fixedRateString = "5000")
    private void cleanup() {
        synchronized (MUTEX) {
            //jobs
            jobsInProgress.clear();
            for (Iterator<Map.Entry<String, CompletableFuture<String>>> i = asyncs.entrySet().iterator(); i.hasNext() ;) {
                Map.Entry<String, CompletableFuture<String>> entry =  i.next();
                if (entry.getValue().isDone()) {
                    try {
                        addMessage(new Alert("success", entry.getKey() + " finished: "+ entry.getValue().get()));
                        setStale();
                        i.remove();
                    } catch (Exception e) {
                        addMessage(new Alert("error",e.getMessage()));
                        log.error(e.getMessage(), e);
                        i.remove();
                    }
                } else {
                    jobsInProgress.add(entry.getKey());
                }
            }

            //sessions
            for(Iterator<Map.Entry<String, Long>> i = sessions.entrySet().iterator();i.hasNext();) {
                Map.Entry<String, Long> entry = i.next();
                if (System.currentTimeMillis() - entry.getValue() > 60000) {
                    i.remove();
                    stale.remove(entry.getKey());
                    messageStack.remove(entry.getKey());
                    log.debug("Session "+entry.getKey()+ " REMOVED");
                }
            }

        }
    }

    public boolean isStale(String sessionId) {
        synchronized (MUTEX) {
            Boolean st = stale.get(sessionId);
            if (st == null) st = Boolean.FALSE;
            stale.put(sessionId, Boolean.FALSE);
            return st;
        }
    }

    public void setStale() {
        synchronized (MUTEX) {
            for (String session:sessions.keySet()) {
                stale.put(session, Boolean.TRUE);
            }
        }
    }

    public boolean isLock() {
        return lock > 0;
    }

    private String getStatusMessage() {
        String msg;
        synchronized (MUTEX) {
            if (jobsInProgress.size() == 0) {
                msg = "Lock["+isLock()+"], Ready.";
            } else {
                msg = "Lock["+isLock()+"], Running: " + jobsInProgress.toString();
            }
        }
        return msg;
    }

    public void addMessage(Alert alert) {
        synchronized (MUTEX) {
            for (String session:sessions.keySet()) {
                List<Alert> alerts = messageStack.get(session);
                if (alerts == null) alerts = new ArrayList<>();
                alerts.add(alert);
                messageStack.put(session, alerts);
            }
        }
    }

    private List<Alert> getMessageStack(String sessionId) {
        List<Alert> msgs = new ArrayList<>();
        synchronized (MUTEX) {
            List<Alert> alerts = messageStack.get(sessionId);
            if (alerts != null) {
                msgs.addAll(alerts);
                messageStack.put(sessionId, new ArrayList<>());
            }
        }
        return  msgs;
    }

    public PollResult poll(String sessionId) throws ServiceException {
        log.trace("Session " + sessionId + " polling...");
        synchronized (MUTEX) {
            sessions.put(sessionId, System.currentTimeMillis());
        }
        return new PollResult(sessionId, getMessageStack(sessionId), getStatusMessage(), isStale(sessionId), isLock());
    }

    public Collection<Silence> getSilences() {
        synchronized (this.silences) {
            return silences;
        }
    }

    public void setSilences(List<Silence> silences) {
        synchronized (this.silences) {
            this.silences.clear();
            this.silences.addAll(silences);
        }
    }

    public void removeSilence(String id) {
        synchronized (this.silences) {
            silences.removeIf(s -> id.equals(s.getId()));
        }
    }

    public void addSilence(Silence silence) {
        synchronized (this.silences) {
            silences.remove(silence);
            silences.add(silence);
        }
    }
}
