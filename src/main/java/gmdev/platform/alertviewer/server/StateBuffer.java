package gmdev.platform.alertviewer.server;

import gmdev.platform.alertviewer.data.AlertManagerUser;
import gmdev.platform.alertviewer.data.AlertManagerUserRepo;
import gmdev.platform.alertviewer.data.silence.Silence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
public class StateBuffer {
    private static final Logger log = LoggerFactory.getLogger(StateBuffer.class);

    private final Object MUTEX = new Object();
    private Long lock = 0L;
    private final Map<String, CompletableFuture<String>> asyncs = new HashMap<>();
    private final List<String> jobsInProgress = new ArrayList<>();
    private final Map<String, List<Alert>> messageStack = new HashMap<>();
    private final Map<String, Boolean> stale = new HashMap<>();
    private final Map<String, Registration> sessions = new HashMap<>();

    private final Set<Silence> silences = new HashSet<>();

    @Autowired
    AlertManagerUserRepo userRepo;

    public boolean aquireLock() {
        synchronized (MUTEX) {
            if (lock > 0) {
                return false;
            }
            lock = System.currentTimeMillis();
            return true;
        }
    }

    public void releaseLock() {
        synchronized (MUTEX) {
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
            for (Iterator<Map.Entry<String, CompletableFuture<String>>> i = asyncs.entrySet().iterator(); i.hasNext(); ) {
                Map.Entry<String, CompletableFuture<String>> entry = i.next();
                if (entry.getValue().isDone()) {
                    try {
                        addMessage(new Alert("success", entry.getKey() + " finished: " + entry.getValue().get()));
                        setStale();
                        i.remove();
                    } catch (Exception e) {
                        addMessage(new Alert("error", e.getMessage()));
                        log.error(e.getMessage(), e);
                        i.remove();
                    }
                } else {
                    jobsInProgress.add(entry.getKey());
                }
            }
        }

        synchronized (MUTEX) {
            for(Iterator<Map.Entry<String, Registration>> i = sessions.entrySet().iterator();i.hasNext();) {
                Map.Entry<String, Registration> entry = i.next();
                if (System.currentTimeMillis() - entry.getValue().getTimestamp() > 60000) {
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

    public boolean isValidSession(String sessionId) {
        synchronized (MUTEX) {
            if (sessions.containsKey(sessionId)) {
                sessions.get(sessionId).update();
                return true;
            }
            return false;
        }
    }

    public boolean isAdmin(String sessionId) {
        synchronized (MUTEX) {
            if (sessions.containsKey(sessionId)) {
                return "admin".equals(sessions.get(sessionId).getRole());
            }
            return false;
        }
    }

    public boolean isLock() {
        return lock > 0;
    }

    private String getStatusMessage() {
        String msg;
        synchronized (MUTEX) {
            if (jobsInProgress.isEmpty()) {
                msg = "Ready.";
            } else {
                msg = "Running: " + jobsInProgress.toString();
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
        return new PollResult(sessionId, getMessageStack(sessionId), getStatusMessage(), isStale(sessionId), isLock());
    }

    public Registration registerSession(String dn) throws AuthenticationException {
        try {
            synchronized (MUTEX) {
                log.info("Session " + dn + " registering...");

                AlertManagerUser user = userRepo.findByDn(dn);
                if (user != null) {
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    md.update(dn.getBytes());
                    byte[] digest = md.digest();
                    String hash = DatatypeConverter.printHexBinary(digest).toUpperCase();
                    String token = "cortana:" + UUID.randomUUID() + ":" + hash;

                    Registration reg = new Registration(user, token);
                    sessions.put(token, reg);
                    log.info("Session " + dn + " registered...");

                    return reg;
                } else {
                    throw new AuthenticationCredentialsNotFoundException(dn + " is not authorized");
                }
            }
        } catch(Exception e) {
            throw new AuthenticationServiceException(e.getMessage());
        }
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
