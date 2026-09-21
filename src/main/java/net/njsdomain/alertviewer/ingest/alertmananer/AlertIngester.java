package net.njsdomain.alertviewer.ingest.alertmananer;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.client.result.DeleteResult;
import net.njsdomain.alertviewer.data.AlertLabelValue;
import net.njsdomain.alertviewer.data.AlertManagerConfig;
import net.njsdomain.alertviewer.data.AlertManagerEntry;
import net.njsdomain.alertviewer.data.AlertManagerEntryRepo;
import net.njsdomain.alertviewer.data.routing.AlertManagerRouting;
import net.njsdomain.alertviewer.data.jira.WraithTickets;
import net.njsdomain.alertviewer.data.jira.WraithUrls;
import javax.xml.bind.DatatypeConverter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import net.njsdomain.alertviewer.data.MetaDataHelper;
import net.njsdomain.alertviewer.data.alert.Alert;
import net.njsdomain.alertviewer.data.silence.Silence;
import net.njsdomain.alertviewer.ingest.Ingester;
import net.njsdomain.alertviewer.server.CustomDateDeserializer;
import net.njsdomain.alertviewer.server.StateBuffer;
import net.njsdomain.alertviewer.util.LogEntryStatus;
import net.njsdomain.alertviewer.util.SSLContextFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
@ConditionalOnProperty(value = "ingester.type", havingValue = "alertmanager")
public class AlertIngester implements Ingester {

    private static final Logger log = LoggerFactory.getLogger(AlertIngester.class);

    @Autowired
    Environment env;

    @Autowired
    AlertManagerClient alertManagerClient;

    @Autowired
    AlertManagerConfigParser configParser;

    @Autowired
    MetaDataHelper meta;

    @Autowired
    MongoTemplate mongo;

    @Autowired
    AlertManagerEntryRepo repo;

    @Autowired
    StateBuffer state;

    @Autowired
    SSLContextFactory sslContextFactory;

    @Autowired
    WraithUrls wraith;

    @Override
    public void ingest() {
        List<Silence> silences = new ArrayList<>();
        int max = 0;
        int online = 0;
        for (AlertManagerConfig c:state.getAlertmanagers()) {
            max++;
            boolean b = ingest(c);
            if (b) {
                online++;
            }
            silences.addAll(getSilences(c));
            refreshRouting(c);
        }
        log.debug("FINISHED INGEST, there are "+silences.size()+" silences");
        state.setSilences(silences);
        //once per cycle, not once per alertmanager, and after the alerts are in: a
        //slow jira should never hold up reading the alertmanagers
        refreshJiraStatuses();
        state.setLastIngestAttempt();
        if (online > 0) state.setLastIngestSuccess();
        state.setAlertManagerStatus(max, online);
    }

    /**
     * Re-read one alertmanager's routing tree from its status api.
     *
     * A failure here never clears what is already cached and never touches the
     * alertmanager's up/down state: not being able to read the config says nothing
     * about whether alerts are ingesting, and blanking the routes screen over a
     * blip would be worse than showing the last known tree marked stale.
     */
    private void refreshRouting(AlertManagerConfig amConfig) {
        AlertManagerRouting previous = state.getRouting(amConfig.getName());
        try {
            String yaml = alertManagerClient.getRoutingYaml(state, amConfig);
            if (yaml == null) {
                markRoutingStale(amConfig, previous, "Could not read the configuration from " + amConfig.getName());
                return;
            }
            String hash = hash(yaml);
            if (previous != null && previous.isAvailable() && hash != null && hash.equals(previous.getConfigHash())) {
                //unchanged since the last cycle, so skip the reparse
                previous.setStale(false);
                previous.setError(null);
                previous.setFetchedAt(System.currentTimeMillis());
                return;
            }
            AlertManagerRouting routing = configParser.parse(amConfig.getName(), yaml);
            routing.setConfigHash(hash);
            state.setRouting(amConfig.getName(), routing);
            log.debug("Refreshed routing config for " + amConfig.getName()
                    + " (" + routing.getReceivers().size() + " receivers)");
        } catch (Exception e) {
            log.warn("Unable to refresh the routing config for " + amConfig.getName() + ": " + e.getMessage());
            markRoutingStale(amConfig, previous, "Could not read the configuration: " + e.getMessage());
        }
    }

    private void markRoutingStale(AlertManagerConfig amConfig, AlertManagerRouting previous, String error) {
        AlertManagerRouting routing = (previous != null) ? previous : new AlertManagerRouting(amConfig.getName());
        routing.setStale(true);
        routing.setError(error);
        state.setRouting(amConfig.getName(), routing);
    }

    private String hash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return DatatypeConverter.printHexBinary(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            //without a hash the config simply reparses every cycle, which is harmless
            return null;
        }
    }



    private boolean isFlappingExpired(AlertManagerEntry entry) {
        Instant i = Instant.ofEpochMilli(entry.getLastChange());
        Instant now = Instant.now();
        boolean b = i.isBefore(now.minus(Duration.ofMinutes(Integer.parseInt(env.getProperty("flapping.timeout.minutes", "15")))));
        long delta = (now.toEpochMilli() - i.toEpochMilli()) / 1000;
        log.debug("Checking "+entry.getId()+" for flapping expired: lastchange="+i.toString()+", now="+now+", deltasecs="+delta+", return "+b);
        return b;
    }

    public Alert jsonToAlert(String jsonAlert) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        log.debug("Alert JSON: "+jsonAlert);
        return objectMapper.readValue(jsonAlert, Alert.class);
    }

    public boolean ingest(AlertManagerConfig amConfig) {
        log.info("AlertManager Ingester running for alertmanager "+amConfig.getName());
        state.getAlertManagersAll().add(amConfig.getName());
        try {
            HttpRequest request = HttpRequest.newBuilder().timeout(Duration.ofSeconds(10))
                    .uri(new URI(amConfig.getAlertsUrl()))
                    .GET()
                    .build();

            JSONArray data = alertManagerClient.sendRequest(state, amConfig, request);
            if (data == null) {
                return false;
            }

            List<AlertManagerEntry> allActiveMinusDatabased = repo.findAll();
            log.debug("Found "+allActiveMinusDatabased.size()+" alerts in database at start of ingest");

            //alerts are transient, so the teams and environments they name are
            //collected as they go past and kept somewhere that outlives them
            Map<String, Set<String>> labelValues = new HashMap<>();

            //process the incoming active alerts
            for (int i = 0;i < data.length();i++) {
                String jsonAlert = data.getJSONObject(i).toString();
                Alert alertFromAlertmanager = jsonToAlert(jsonAlert);
                log.debug("Ingest processing alert from alertmanager: "+alertFromAlertmanager.getFingerprint());
                Optional<AlertManagerEntry> alertFromDatabase = repo.findByIdAndAlertmanager(alertFromAlertmanager.getFingerprint(), amConfig.getName());
                AlertManagerEntry databaseEntry;
                if (alertFromDatabase.isPresent()) {
                    databaseEntry = alertFromDatabase.get();
                    databaseEntry.setAlert(alertFromAlertmanager);
                    log.debug("Existing alert will be updated to: " + alertFromAlertmanager);

                    //RESOLVED alert firing again...
                    if (LogEntryStatus.RESOLVED.equals(databaseEntry.getStatus())) {
                        log.debug("Resolved alert is firing again");

                        //is it flapping?
                        if (!databaseEntry.isAcked() && !isFlappingExpired(databaseEntry)) {
                            databaseEntry.addNote("System", "Alert is FLAPPING");
                            log.info("Firing alert "+databaseEntry.getId()+" is flapping");
                            databaseEntry.setFlapping(true);
                        } else if (isFlappingExpired(databaseEntry)) {
                            log.debug("Firing alert "+databaseEntry.getId()+", which was RESOLVED, is not flapping due to time elapsed");
                        } else {
                            log.debug("else case");
                        }

                        //the ack covered the occurrence that ended. This is fresh
                        //firing, so drop it and let the alert be seen again. It used
                        //to happen by itself, when acking wrote over the status and
                        //this line wrote NEW back over that
                        if (databaseEntry.isAcked()) {
                            log.info("Resolved alert "+databaseEntry.getId()+" is firing again, clearing its ack");
                            databaseEntry.addNote("System", "Alert is firing again, ACK cleared");
                            databaseEntry.setAcked(false);
                        }

                        databaseEntry.addNote("System", "Previously RESOLVED alert is now NEW");
                        databaseEntry.setStatus(LogEntryStatus.NEW); //changes lastchange
                    }

                    //firing alert is suppressed...
                    if ("suppressed".equals(alertFromAlertmanager.getStatus().getState())) {
                        if (!LogEntryStatus.SILENCED.equals(databaseEntry.getStatus())) {
                            databaseEntry.addNote("System", "Alert is SILENCED");
                            log.debug("Alert is silenced");
                            if (databaseEntry.isFlapping()) {
                                log.info("Silenced alert " + databaseEntry.getId() + " is no longer flapping(1)");
                                databaseEntry.addNote("System", "Alert is no longer FLAPPING");
                                databaseEntry.setFlapping(false);
                            }
                            databaseEntry.setStatus(LogEntryStatus.SILENCED); //changes lastchange
                        }

                    //SILENCED alert is no longer suppressed...
                    } else if (LogEntryStatus.SILENCED.equals(databaseEntry.getStatus())) {
                        if (!"suppressed".equals(alertFromAlertmanager.getStatus().getState())) {
                            databaseEntry.addNote("System", "Previously SILENCED alert is now NEW");
                            if (databaseEntry.isFlapping()) {
                                log.info("Firing alert " + databaseEntry.getId() + " is no longer flapping(2)");
                                databaseEntry.addNote("System", "Alert is no longer FLAPPING");
                                databaseEntry.setFlapping(false);
                            }
                            databaseEntry.setStatus(LogEntryStatus.NEW); //changes lastchange
                        }

                    //NEW alert is still firing...
                    } else if (LogEntryStatus.NEW.equals(databaseEntry.getStatus())) {
                        //should flapping be cleared?
                        if (isFlappingExpired(databaseEntry) && databaseEntry.isFlapping()) {
                            log.info("Firing alert "+databaseEntry.getId()+" is no longer flapping(3)");
                            databaseEntry.addNote("System", "Alert is no longer FLAPPING");
                            databaseEntry.setFlapping(false);
                        //} else {
                            //log.debug("Firing alert "+databaseEntry.getId()+" is still flapping");
                        }
                    }

                    allActiveMinusDatabased.remove(databaseEntry);
                } else {
                    log.debug("Creating new alert record: " + alertFromAlertmanager);
                    databaseEntry = new AlertManagerEntry(alertFromAlertmanager);
                    databaseEntry.addNote("System", "New alert imported from "+amConfig.getName());
                }
                databaseEntry.setAlertmanager(amConfig.getName());
                repo.save(databaseEntry);

                //read after setAlertmanager, so environment has been through both of
                //its fallbacks: Alert.setLabels copies alternative environment label  into it when the
                //alert carries no environment, and setAlertmanager fills in the
                //alertmanager's own name when even that leaves it empty
                noteLabelValue(labelValues, AlertLabelValue.TEAM, databaseEntry);
                noteLabelValue(labelValues, AlertLabelValue.ENVIRONMENT, databaseEntry);
            }
            recordLabelValues(amConfig.getName(), labelValues);

            //process existing alerts that are not existing
            log.debug("Database still has "+allActiveMinusDatabased.size()+" alerts that were not present in request");
            for (AlertManagerEntry entry:allActiveMinusDatabased) {
                if (!LogEntryStatus.RESOLVED.equals(entry.getStatus()) && amConfig.getName().equals(entry.getAlertmanager())) {
                    if (LogEntryStatus.NEW.equals(entry.getStatus()) && entry.isFlapping() && !isFlappingExpired(entry)) {
                        log.debug("Firing alert "+entry.getId()+" is resolved but is flapping, keep it as new");
                        entry.setFlapping(true);
                    } else {
                        if (entry.isFlapping()) {
                            log.info("Resolved alert "+entry.getId()+" is no longer flapping(4)");
                            entry.addNote("System", "Alert is no longer FLAPPING");
                            entry.setFlapping(false);
                        }
                        entry.setStatus(LogEntryStatus.RESOLVED); //changes lastchange
                        entry.addNote("System", "Alert is now RESOLVED");
                        log.debug("This alert is now resolved");
                        repo.save(entry);
                    }
                }
            }

            //timeout resolved alerts, except the ones jira still has open work on
            expireResolved();
            state.getAlertManagersUp().add(amConfig.getName());

            return true;
        } catch(Exception e) {
            log.error("Error reading alerts from "+amConfig.getName(), e);
            state.getAlertManagersUp().remove(amConfig.getName());
            return false;
        }
    }

    /**
     * Drop resolved alerts once they are older than resolved.remove.minutes, except
     * the ones jira still has an open ticket for.
     *
     * An alert whose ticket is open is the one thing here worth keeping: the work is
     * not finished just because the alert stopped firing, and losing the alert loses
     * the context behind the ticket. Once that ticket closes the next cycle takes the
     * alert away, so nothing is kept forever.
     */
    private void expireResolved() {
        String minutes = env.getProperty("resolved.remove.minutes", "10080");
        LocalDateTime date = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(Integer.parseInt(minutes));
        Query query = new Query();
        query.addCriteria(Criteria.where("status").is(LogEntryStatus.RESOLVED)
                .andOperator(Criteria.where("alert.endsAt").lte(date)));

        List<AlertManagerEntry> expired = mongo.find(query, AlertManagerEntry.class);
        if (expired.isEmpty()) {
            return;
        }

        //nothing is tracking an alert without a ticket, so it goes as it always did
        List<String> removable = new ArrayList<>();
        List<AlertManagerEntry> ticketed = new ArrayList<>();
        for (AlertManagerEntry entry : expired) {
            String key = entry.getJiraKey();
            if (key == null || key.isBlank()) {
                removable.add(entry.getId());
            } else {
                ticketed.add(entry);
            }
        }

        if (!ticketed.isEmpty()) {
            removable.addAll(closedTicketIds(ticketed));
        }
        if (removable.isEmpty()) {
            return;
        }

        DeleteResult dr = mongo.remove(new Query(Criteria.where("id").in(removable)), AlertManagerEntry.class);
        if (dr.getDeletedCount() > 0) {
            int held = expired.size() - (int) dr.getDeletedCount();
            log.info(dr.getDeletedCount() + " RESOLVED alerts were deleted after " + minutes + " minutes"
                    + ((held > 0) ? ", " + held + " held for their jira ticket" : ""));
        }
    }

    /**
     * Ask wraith about these alerts' tickets in one request, refreshing the status
     * stored against the ones still open, and return the ids safe to remove.
     *
     * The lookup is by ticket key rather than by the alert's label, because a ticket
     * linked by hand never carried that label and a label search would report nothing
     * for it. Anything short of a clean answer holds every alert: jira being
     * unreachable is no evidence that the work is done, and keeping an alert too long
     * costs disk where deleting it early loses the record for good.
     */
    private List<String> closedTicketIds(List<AlertManagerEntry> ticketed) {
        List<String> closed = new ArrayList<>();
        String url = jiraLookupUrl();
        if (url == null) {
            //this deployment does not use jira, so there is nothing to hold these
            //alerts and they expire on age alone, the way they always did
            log.debug("No jira lookup configured, expiring " + ticketed.size()
                    + " resolved alerts on age alone");
            for (AlertManagerEntry entry : ticketed) {
                closed.add(entry.getId());
            }
            return closed;
        }

        List<String> keys = new ArrayList<>();
        for (AlertManagerEntry entry : ticketed) {
            keys.add(ticketKey(entry));
        }

        JsonNode found = readJiraIssues(url, keys);
        if (found == null) {
            log.warn("Holding " + ticketed.size() + " resolved alerts with tickets, jira could not be read");
            return List.of();
        }

        for (AlertManagerEntry entry : ticketed) {
            JsonNode issue = found.path(ticketKey(entry));
            if (!issue.isObject()) {
                //wraith answers for every key it was given, so this should not happen.
                //Hold the alert rather than read the silence as permission to delete
                log.warn("Holding resolved alert " + entry.getId() + ": jira said nothing about " + entry.getJiraKey());
                continue;
            }

            //a closed ticket is finished work, and a ticket jira no longer has is not
            //holding anything either, so both let the alert go. Wraith only reports a
            //ticket missing on a 404 from jira, never on an outage
            if (WraithTickets.isClosed(issue) || WraithTickets.isNotFound(issue)) {
                closed.add(entry.getId());
                continue;
            }

            //still open, so keep the alert and record where jira has got to
            applyTicket(entry, issue);
            log.debug("Holding resolved alert " + entry.getId() + ": jira " + entry.getJiraKey() + " is still open");
        }
        return closed;
    }

    //wraith keys its answer by the key it was asked about, normalised the same way
    private static String ticketKey(AlertManagerEntry entry) {
        return entry.getJiraKey().trim().toUpperCase();
    }

    /**
     * Note one of an alert's label values for the curated list, ignoring the ones that
     * say nothing. Values are kept exactly as the alert spelled them, so two spellings
     * of the same team stay two entries rather than one arbitrary winner.
     */
    private void noteLabelValue(Map<String, Set<String>> into, String type, AlertManagerEntry entry) {
        String value = entry.getAlert().getLabels().get(type);
        if (value == null || value.isBlank()) {
            return;
        }
        into.computeIfAbsent(type, k -> new HashSet<>()).add(value.trim());
    }

    /**
     * Write the teams and environments this alertmanager's alerts named into the
     * curated list, under that alertmanager.
     *
     * Upserting by id makes the dedupe the database's problem: a value already there
     * only has its lastSeen moved on, which is what tells a team still in use apart
     * from one that has not been seen for months. Failing here is not worth losing an
     * ingest over, so it is logged and left.
     */
    private void recordLabelValues(String alertmanager, Map<String, Set<String>> labelValues) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int added = 0;
        for (Map.Entry<String, Set<String>> entry : labelValues.entrySet()) {
            String type = entry.getKey();
            for (String value : entry.getValue()) {
                try {
                    boolean inserted = mongo.upsert(
                            Query.query(Criteria.where("_id").is(AlertLabelValue.idFor(alertmanager, type, value))),
                            new Update()
                                    .setOnInsert("alertmanager", alertmanager)
                                    .setOnInsert("type", type)
                                    .setOnInsert("value", value)
                                    .setOnInsert("firstSeen", now)
                                    .set("lastSeen", now),
                            AlertLabelValue.class).getUpsertedId() != null;
                    if (inserted) {
                        added++;
                        log.info("Curated list has a new "+type+" on "+alertmanager+": "+value);
                    }
                } catch (Exception e) {
                    log.warn("Could not record "+type+" '"+value+"' on "+alertmanager+" in the curated list", e);
                }
            }
        }
        if (added > 0) {
            log.debug("Added "+added+" values to "+alertmanager+"'s curated label lists");
        }
    }

    /**
     * Where to ask about jira tickets, or null when this deployment does not use jira.
     *
     * Every jira check here goes through this, so an install with no wraith behind it
     * quietly skips the lot rather than logging a failed call every cycle.
     */
    private String jiraLookupUrl() {
        return wraith.getIssues();
    }

    /**
     * Bring the jira status stored against every ticketed alert back in step with
     * jira, so what the UI shows is what jira currently says rather than what it said
     * when the ticket was raised.
     *
     * One request covers every ticket. A failure changes nothing: the stored status
     * stays as it was and the next cycle tries again.
     */
    private void refreshJiraStatuses() {
        String url = jiraLookupUrl();
        if (url == null) {
            return;
        }

        List<AlertManagerEntry> ticketed = mongo.find(new Query(new Criteria().andOperator(
                Criteria.where("jiraKey").exists(true),
                Criteria.where("jiraKey").ne(null),
                Criteria.where("jiraKey").ne(""))), AlertManagerEntry.class);
        if (ticketed.isEmpty()) {
            return;
        }

        List<String> keys = new ArrayList<>();
        for (AlertManagerEntry entry : ticketed) {
            keys.add(ticketKey(entry));
        }

        JsonNode found = readJiraIssues(url, keys);
        if (found == null) {
            log.warn("Could not refresh jira status for " + ticketed.size() + " alerts, leaving them as they are");
            return;
        }

        int changed = 0;
        for (AlertManagerEntry entry : ticketed) {
            JsonNode issue = found.path(ticketKey(entry));
            if (issue.isObject() && applyTicket(entry, issue)) {
                changed++;
            }
        }
        if (changed > 0) {
            log.info("Refreshed the jira status of " + changed + " of " + ticketed.size() + " ticketed alerts");
        }
    }

    /**
     * Write what jira says about a ticket onto its alert, and report whether that
     * changed anything. The key is written too, since a ticket that moved project
     * answers under a new one.
     */
    private boolean applyTicket(AlertManagerEntry entry, JsonNode issue) {
        String key = WraithTickets.key(issue);
        String status = WraithTickets.status(issue);
        if (key == null || (key.equals(entry.getJiraKey()) && Objects.equals(status, entry.getJiraStatus()))) {
            return false;
        }
        entry.setJiraKey(key);
        entry.setJiraStatus(status);
        repo.save(entry);
        return true;
    }

    /**
     * Wraith's answer for these ticket keys, or null when it could not be read.
     * Null is deliberately not the same as "no such ticket": only wraith saying so
     * counts as an answer, so a jira outage never reads as a pile of missing tickets.
     */
    private JsonNode readJiraIssues(String url, List<String> keys) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            HttpRequest request = HttpRequest.newBuilder().timeout(Duration.ofSeconds(60))
                    .uri(new URI(url))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of("keys", keys))))
                    .header("Content-Type", "application/json")
                    .build();
            java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                    .sslContext(sslContextFactory.getSSLContext()).build();
            java.net.http.HttpResponse<String> response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Jira issue lookup of " + keys.size() + " tickets answered "
                        + response.statusCode() + ": " + response.body());
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.warn("Could not reach the jira issue lookup at " + url, e);
            return null;
        }
    }

    public List<Silence> getSilences(AlertManagerConfig amConfig) {
        log.debug("Reading silences for "+amConfig.getName());
        List<Silence> existingSilences = new ArrayList<>();
        try {
            JSONArray silences = alertManagerClient.getSilences(state, amConfig);

            if (silences == null) return existingSilences;
            log.debug("Silences JSON: "+silences.toString());
            for (int i = 0;i < silences.length();i++) {
                String jsonSilence = silences.getJSONObject(i).toString();
                log.debug("processing silence "+i+": "+jsonSilence);
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                final SimpleModule module = new SimpleModule("", Version.unknownVersion());
                module.addDeserializer(LocalDateTime.class, new CustomDateDeserializer());
                objectMapper.registerModule(module);
                Silence silence = objectMapper.readValue(jsonSilence, Silence.class);
                silence.setAlertmanager(amConfig.getName());
                log.debug("silence state is '"+silence.getStatus().getState()+"'");
                if (silence.getStatus().getState().equals("active")) {
                    //set hours based on dates
                    Duration dur = Duration.between(silence.getStartsat(), silence.getEndsat());
                    silence.setHours(dur.toHours());
                    Duration rem = Duration.between(LocalDateTime.now(ZoneOffset.UTC), silence.getEndsat());
                    silence.setHoursLeft(rem.toHours());
                    existingSilences.add(silence);
                }
            }
            log.debug("SILENCES ADDED: "+existingSilences.size());
        } catch(Exception e) {
            log.error("Error reading silences from "+amConfig.getName(), e);
        }
        return existingSilences;

    }


}
