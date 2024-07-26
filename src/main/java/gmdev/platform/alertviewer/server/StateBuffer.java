package gmdev.platform.alertviewer.server;

import gmdev.platform.alertviewer.data.AlertManagerConfig;
import gmdev.platform.alertviewer.data.AlertManagerUser;
import gmdev.platform.alertviewer.data.AlertManagerUserRepo;
import gmdev.platform.alertviewer.data.silence.Silence;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Component
public class StateBuffer {
    private static final Logger log = LoggerFactory.getLogger(StateBuffer.class);


    private Instant lastIngestSuccess = Instant.now();

    private final Object MUTEX = new Object();
    private final Map<String, CompletableFuture<String>> asyncs = new HashMap<>();
    private final Set<String> alertManagersAll = new TreeSet<>();
    private final Set<String> alertManagersUp = new TreeSet<>();

    private final Map<String, List<Alert>> messageStack = new HashMap<>();
    private final Map<String, Boolean> stale = new HashMap<>();

    private final Map<String, Registration> sessions = new HashMap<>();
    private final Map<String, AlertManagerConfig> alertmanagers = new HashMap<>();

    private final Set<Silence> silences = new HashSet<>();

    private AtomicLong ingestDelta = new AtomicLong(0);

    private AtomicLong alertmanagersOnline = new AtomicLong(0);

    private AtomicLong alertmanagersMax = new AtomicLong(0);

    @Autowired
    Environment env;

    @Autowired UserService userService;

    @Autowired
    AlertManagerUserRepo userRepo;


    @PostConstruct
    private void init() {

        try {
            Gauge.builder("cortana.ingest.delta", () ->
                    ingestDelta).strongReference(true).register(Metrics.globalRegistry);

            Gauge.builder("cortana.alertmanagers.max", () ->
                    alertmanagersMax).strongReference(true).register(Metrics.globalRegistry);

            Gauge.builder("cortana.alertmanagers.online", () ->
                    alertmanagersOnline).strongReference(true).register(Metrics.globalRegistry);

            int count = Integer.parseInt(env.getProperty("alertmanager.count"));
            for (int i = 1; i <= count; i++) {
                String amId = ""+i;
                String amName = env.getProperty("alertmanager.name."+amId);
                if (amName == null || amName.isEmpty()) {
                    log.error("Property 'alertmanager.name."+amId+"' is null or empty, there WILL be errors!");
                }

                String url = env.getProperty("alertmanager.url."+amId);
                String name = env.getProperty("alertmanager.name."+amId);
                AlertManagerConfig amc = new AlertManagerConfig(i, name, url);
                alertmanagers.put(name, amc);

            }
        } catch(Exception e) {
            log.error("Unable to read property 'alertmanager.count', which should be a non-zero positive integer");
        }
        log.info("*** StateBuffer Initialized ***");

    }

    public void setLastIngestAttempt() {
        final long value = System.currentTimeMillis() - lastIngestSuccess.toEpochMilli();
        log.debug("INGEST DELTA "+value);
        //Metrics.globalRegistry.gauge("ingest.delta", value);
        ingestDelta.set(value);
    }

    public void setAlertManagerStatus(int max, int online) {
        alertmanagersOnline.set(online);
        alertmanagersMax.set(max);
    }

    public void setLastIngestSuccess() {
        lastIngestSuccess = Instant.now();
    }

    public Instant getLastIngestSuccess() {
        return lastIngestSuccess;
    }

    public Collection<AlertManagerConfig> getAlertmanagers() {
        return alertmanagers.values();
    }

    public List<String> getAlertmanagerNames() {
        List<String> c = new ArrayList<>();
        for (AlertManagerConfig amc:alertmanagers.values()) {
            c.add(amc.getName());
        }
        return c;
    }

    public AlertManagerConfig getAlertmanager(String name) {
        return alertmanagers.get(name);
    }

    public void addAsync(String name, CompletableFuture<String> future) {
        asyncs.put(name, future);
    }




    public Set<String> getAlertManagersAll() {
        return alertManagersAll;
    }

    public Set<String> getAlertManagersUp() {
        return alertManagersUp;
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

    public String getUser(String sessionId) {
        synchronized (MUTEX) {
            if (sessions.containsKey(sessionId)) {
                return sessions.get(sessionId).getUser();
            }
            return "unknown";
        }
    }


    private String getStatusMessage() {
        boolean stale = ingestDelta.get() > 60000;
        String msg;
        if (alertmanagersMax.get() == 0) {
            msg = "ERROR: No alertmanagers registered.";
            log.error(msg);
        } else if (stale) {
            msg = "ERROR: No response from alertmanagers within scheduled time.";
            log.error(msg);
        } else {
            msg = "Ready version " + env.getProperty("build.version") + ".";
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
        return new PollResult(sessionId, getMessageStack(sessionId), getStatusMessage(),
                lastIngestSuccess, env.getProperty("build.version"), alertManagersAll, alertManagersUp);
    }

    public Registration registerSession(String dn, String ingressUsername) throws AuthenticationException {
        try {
            synchronized (MUTEX) {
                log.info("Session " + dn + " registering...");

                AlertManagerUser user;
                Boolean testmode = Boolean.valueOf(env.getProperty("testmode.enabled"));
                if (testmode) {
                    log.warn("*** TEST MODE ENABLED, ALLOWING TEST DN ***");
                    if ("TESTMODE:testuser".equals(dn)) {
                        user = userRepo.findByDn(dn);
                        if (user == null) {
                            user = new AlertManagerUser("testuser", dn, "admin", true);
                            userService.saveUser(user);
                        }
                    } else {
                        user = null;
                    }
                } else {
                    user = userRepo.findByDn(dn);
                }

                //automatically provision new ingress users
                if (user == null && ingressUsername != null && !ingressUsername.isEmpty()) {
                    user = new AlertManagerUser(ingressUsername, dn, "user", true);
                    userService.saveUser(user);
                }

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

    public String removeSilenceAndGetAlertManager(String id) {
        synchronized (this.silences) {
            for(Iterator<Silence> i = silences.iterator();i.hasNext();) {
                Silence s = i.next();
                if (id.equals(s.getId())) {
                    i.remove();
                    return s.getAlertmanager();
                }
            }
        }
        return null;
    }

    public Silence getSilence(String id) {
        synchronized (this.silences) {
            for (Silence s : silences) {
                if (id.equals(s.getId())) {
                    return s;
                }
            }
        }
        return null;
    }

    public void addSilence(Silence silence) {
        synchronized (this.silences) {
            silences.remove(silence);
            silences.add(silence);
        }
    }
}
