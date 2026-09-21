package net.njsdomain.alertviewer.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.njsdomain.alertviewer.data.AlertLabelValue;
import net.njsdomain.alertviewer.data.AlertLabelValueRepo;
import net.njsdomain.alertviewer.data.AlertManagerEntry;
import net.njsdomain.alertviewer.data.AlertManagerEntryRepo;
import net.njsdomain.alertviewer.data.AlertManagerUser;
import net.njsdomain.alertviewer.data.alert.Alert;
import net.njsdomain.alertviewer.data.jira.WraithGeneric;
import net.njsdomain.alertviewer.data.jira.WraithTickets;
import net.njsdomain.alertviewer.data.jira.WraithUrls;
import net.njsdomain.alertviewer.data.silence.Silence;
import net.njsdomain.alertviewer.util.SSLContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class RestEndpoint {
    private static final Logger log = LoggerFactory.getLogger(RestEndpoint.class);

    @Autowired SSLContextFactory sslContextFactory;
    @Autowired RequestService requestService;
    @Autowired UserService userService;
    @Autowired MarkService markService;
    @Autowired StateBuffer state;
    @Autowired
    AlertManagerEntryRepo logRepo;

    @Autowired
    AlertLabelValueRepo labelValueRepo;

    @Autowired
    WraithUrls wraith;
    @Autowired
    Environment env;

    @GetMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServiceResponse<RequestResponse>> login(
            @RequestHeader(value = "Authorization", required = false) String credentials,
            @RequestHeader(value = "CORTANA-SSL-CERT", required = false) String certString,
            @RequestHeader(value = "X-WEBAUTH-USER", required = false) String ingressUser) {
        try {
            String dn = null;
            Boolean testmode = Boolean.valueOf(env.getProperty("testmode.enabled"));
            if (testmode) {
                dn = "TESTMODE:testuser";
                ingressUser = "testuser";
            } else if (ingressUser != null) {
                //assume this is a valid user
                dn = "INGRESS:"+ingressUser;
            } else if (certString != null) {
                String certStringDecoded = URLDecoder.decode(certString, "UTF-8");
                X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(certStringDecoded.getBytes("UTF-8")));
                dn = cert.getSubjectDN().toString().replaceAll("\\s+", "");
            } else if (credentials != null && credentials.startsWith("Basic ")) {
                String basicCreds = new String(Base64.getDecoder().decode(credentials.substring(6)));
                dn = basicCreds.split(":")[0];
                log.debug("Using basic username as DN: "+dn);
            } else {
                log.error("Login error: No credentials");
                return new ResponseEntity<>(new ServiceResponse<>("No Credentials provided"), HttpStatus.UNAUTHORIZED);
            }
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setAccessControlExposeHeaders(List.of("CORTANA-banner", "CORTANA-token", "CORTANA-user", "CORTANA-role", "CORTANA-retention", "CORTANA-jira-url", "CORTANA-jira-label-prefix", "CORTANA-jira-enabled"));
            Registration reg = state.registerSession(dn, ingressUser);
            responseHeaders.set("CORTANA-TOKEN", reg.getToken());
            responseHeaders.set("CORTANA-USER", reg.getUser());
            responseHeaders.set("CORTANA-ROLE", reg.getRole());
            responseHeaders.set("CORTANA-BANNER", env.getProperty("banner.text"));
            //how far back the timeline graph can usefully be zoomed out: anything
            //older has already been swept by the ingester's RESOLVED cleanup
            responseHeaders.set("CORTANA-RETENTION", env.getProperty("resolved.remove.minutes", "10080"));
            //base of the jira the tickets land in; the UI appends /browse/<key> to reach one
            responseHeaders.set("CORTANA-JIRA-URL", env.getProperty("jira.base.url", ""));
            //prefix on the jira label carrying the fingerprint, so the details panel can
            //show the same label the rebuild searches on
            responseHeaders.set("CORTANA-JIRA-LABEL-PREFIX", env.getProperty("jira.label.prefix", "alertmanager"));
            //jira runs through wraith, so with no wraith url at all there is no jira
            //here and the UI hides the controls rather than offering dead buttons
            responseHeaders.set("CORTANA-JIRA-ENABLED", String.valueOf(wraith.enabled()));
            return new ResponseEntity("login successful", responseHeaders, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Login error: "+e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.UNAUTHORIZED);
        }
    }

    @GetMapping(value = "/export", produces = MediaType.TEXT_PLAIN_VALUE)
    public String export(@RequestHeader("CORTANA-TOKEN") String token,
                          @RequestParam(required = false,value = "severity")List<String> severity,
                          @RequestParam(required = false,value = "start")String start,
                          @RequestParam(required = false,value = "end")String end,
                          @RequestParam(required = false,value = "statuses")List<String> statuses,
                          @RequestParam(required = false,value = "environments")List<String> environments,
                          @RequestParam(required = false,value = "callinOnly",defaultValue = "false")boolean callinOnly,
                          HttpServletResponse response) {

        if (!state.isValidSession(token)) {
            log.info("Unauthorized endpoint access: export");
            return "Unauthorized, please login";
        }

        response.setHeader("Content-Disposition", "attachment; filename=export.txt");
        try {
            return requestService.request(severity, start, end, statuses, environments, null, true, callinOnly).getContent();
        } catch (ServiceException e) {
            log.error("Export error: "+e.getMessage(), e);
            return "ERROR: "+e.getMessage();
        }
    }


    @GetMapping(value = "/alerts", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServiceResponse<RequestResponse>> request(@RequestHeader("CORTANA-TOKEN") String token,
                                    @RequestParam(required = false,value = "severity")List<String> severity,
                                    @RequestParam(required = false,value = "start")String start,
                                    @RequestParam(required = false,value = "end")String end,
                                    @RequestParam(required = false,value = "statuses")List<String> statuses,
                                    @RequestParam(required = false,value = "groupField")String groupField,
                                    @RequestParam(required = false,value = "environments")List<String> environments,
                                    //absent means "every alert", so old links keep working
                                    @RequestParam(required = false,value = "callinOnly",defaultValue = "false")boolean callinOnly) {

        try {
            if (!state.isValidSession(token)) {
                log.info("Unauthorized endpoint access: request");
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }
            log.debug("Query: statuses="+statuses);
            return new ResponseEntity<>(new ServiceResponse<>("Query complete", requestService.request(severity, start, end, statuses, environments, groupField, false, callinOnly)), HttpStatus.OK);
        } catch (ServiceException e) {
            log.error("Request error: "+e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    //the routing tree of every alertmanager, as read from their live status apis.
    //served from the ingester's cache, so opening the screen costs no round trip to
    //alertmanager. a dedicated endpoint rather than a field on the alerts payload:
    //this is large and near-static, and /alerts is polled every 30 seconds.
    @GetMapping(value = "/routes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServiceResponse<RoutingResponse>> routes(@RequestHeader("CORTANA-TOKEN") String token) {
        try {
            if (!state.isValidSession(token)) {
                log.info("Unauthorized endpoint access: routes");
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }
            return new ResponseEntity<>(new ServiceResponse<>("Query complete",
                    new RoutingResponse(state.getAllRouting())), HttpStatus.OK);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServiceResponse<UsersResponse>> users(@RequestHeader("CORTANA-TOKEN") String token) {

        try {
            if (!state.isAdmin(token)) {
                log.info("Unauthorized endpoint access: users");
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }

            return new ResponseEntity<>(new ServiceResponse<>("Query complete", userService.getUsers()), HttpStatus.OK);
        } catch (ServiceException e) {
            log.error("Users error: "+e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping(value = "/user", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServiceResponse<Void>> saveUser(@RequestHeader("CORTANA-TOKEN") String token,
                                                      @RequestBody AlertManagerUser user) {
        if (!state.isAdmin(token)) {
            log.info("Unauthorized endpoint access: save user");
            return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
        }
        userService.saveUser(user);
        return new ResponseEntity<>(new ServiceResponse<>("User added"), HttpStatus.OK);
    }
    @DeleteMapping(value = "/user")
    public ResponseEntity<ServiceResponse<Void>> deleteUser(@RequestHeader("CORTANA-TOKEN") String token,
                                                               @RequestParam(value = "id")String id) {
        if (!state.isAdmin(token)) {
            log.info("Unauthorized endpoint access: delete user");
            return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
        }
        userService.deleteUser(id);
        return new ResponseEntity<>(new ServiceResponse<>("User deleted"), HttpStatus.OK);
    }

    @PutMapping("/mark")
    public ResponseEntity<ServiceResponse<Void>> mark(@RequestHeader("CORTANA-TOKEN") String token,
                      @RequestParam(value = "id")String id,
                      @RequestParam(value = "status")String status) {
        try {
            if (!state.isAdmin(token)) {
                log.info("Unauthorized endpoint access: mark");
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }
            //acking a resolved alert leaves its status alone, so let the service say
            //what actually happened rather than assuming the status was written
            String done = markService.mark(id, status);
            addNote(id, state.getUser(token), done);
            return new ResponseEntity<>(new ServiceResponse<>("Record "+id+": "+done+" by "+state.getUser(token)), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }



    @GetMapping("/poll")
    public ResponseEntity<ServiceResponse<PollResult>> poll(@RequestHeader("CORTANA-TOKEN") String token) {
        try {
            if (!state.isValidSession(token)) {
                log.warn("Unauthorized endpoint access: poll, token="+token);
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }
            PollResult pr = state.poll(token);
            log.debug("Poll received with token="+token);
            return new ResponseEntity<>(new ServiceResponse<>("Poll complete", pr), HttpStatus.OK);
        } catch (ServiceException e) {
            log.error("Error while polling, token="+token, e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @DeleteMapping(value = "/alert")
    public ResponseEntity<ServiceResponse<Void>> delete(@RequestHeader("CORTANA-TOKEN") String token,
                                                        @RequestParam(value = "id")String id) {
        try {
            if (!state.isAdmin(token)) {
                log.info("Unauthorized endpoint access: delete");
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }
            log.info("Deleting record: "+id);
            logRepo.deleteById(id);

            return new ResponseEntity<>(new ServiceResponse<>("Record deleted"), HttpStatus.OK);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @PostMapping(value = "/note", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<ServiceResponse<Void>> note(@RequestHeader("CORTANA-TOKEN") String token,
                                                      @RequestParam(value = "id")String id, @RequestBody String note) {
        try {
            if (!state.isValidSession(token)) {
                log.info("Unauthorized endpoint access: note");
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }
            boolean added = addNote(id, state.getUser(token), note);
            if (added) {
                return new ResponseEntity<>(new ServiceResponse<>("Note added"), HttpStatus.OK);
            } else {
                return new ResponseEntity<>(new ServiceResponse<>("Record not found"), HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * The teams and environments ever seen on one alertmanager, for building an outage
     * silence out of. Alerts are transient, so this is the curated list the ingest has
     * been accumulating rather than whatever happens to be firing right now.
     */
    @GetMapping(value = "/labelvalues", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServiceResponse<Map<String, List<String>>>> labelValues(
            @RequestHeader("CORTANA-TOKEN") String token,
            @RequestParam(value = "alertmanager") String alertmanager) {
        try {
            if (!state.isValidSession(token)) {
                log.info("Unauthorized endpoint access: labelValues");
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }

            List<String> environments = labelValuesOf(alertmanager, AlertLabelValue.ENVIRONMENT);
            //an alert with no environment of its own is labelled with the alertmanager's
            //name, so that name is always a real environment here whether or not any
            //alert has needed it yet. Only add it if the list does not already have it
            if (!environments.contains(alertmanager)) {
                environments.add(0, alertmanager);
            }

            //which labels actually carry an environment on the wire. Alertviewer copies
            //the alternative into "environment" when it reads an alert, but a silence
            //is matched by alertmanager against the raw labels, which have no such copy
            List<String> environmentLabels = new ArrayList<>();
            environmentLabels.add("environment");
            String alternative = Alert.getAlternativeEnvironmentLabel();
            if (alternative != null && !environmentLabels.contains(alternative)) {
                environmentLabels.add(alternative);
            }

            Map<String, List<String>> payload = new LinkedHashMap<>();
            payload.put("environments", environments);
            payload.put("teams", labelValuesOf(alertmanager, AlertLabelValue.TEAM));
            payload.put("environmentLabels", environmentLabels);
            return new ResponseEntity<>(new ServiceResponse<>("Label values", payload), HttpStatus.OK);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    //sorted the way someone reading a list expects, not the way bytes sort
    private List<String> labelValuesOf(String alertmanager, String type) {
        List<String> values = new ArrayList<>();
        for (AlertLabelValue lv : labelValueRepo.findByAlertmanagerAndType(alertmanager, type)) {
            if (lv.getValue() != null && !lv.getValue().isBlank()) {
                values.add(lv.getValue());
            }
        }
        values.sort(String.CASE_INSENSITIVE_ORDER);
        return values;
    }

    @PostMapping(value = "/silence", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServiceResponse<Void>> silence(@RequestHeader("CORTANA-TOKEN") String token,
                                                         @RequestBody String payload) {
        try {
            if (!state.isAdmin(token)) {
                log.info("Unauthorized endpoint access: silence");
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }
            log.debug("SAVE SILENCE: "+payload);

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            Silence silence = objectMapper.readValue(payload, Silence.class);

            String silencesUrl = state.getAlertmanager(silence.getAlertmanager()).getSilencesUrl();

            LocalDateTime dnow = LocalDateTime.now(ZoneOffset.UTC);
            silence.setUpdatedat(dnow);
            if (silence.getStartsat() == null || silence.getEndsat() == null) {
                //the usual case: start now and run for the number of hours asked for
                silence.setStartsat(dnow);
                silence.setEndsat(dnow.plusHours(silence.getHours()));
            } else if (!silence.getEndsat().isAfter(silence.getStartsat())) {
                return new ResponseEntity<>(new ServiceResponse<>("A silence has to end after it starts"), HttpStatus.BAD_REQUEST);
            } else if (!silence.getEndsat().isAfter(dnow)) {
                //alertmanager would refuse it anyway, less helpfully
                return new ResponseEntity<>(new ServiceResponse<>("That silence has already ended (times are UTC)"), HttpStatus.BAD_REQUEST);
            } else {
                //an outage names its own window. Keep hours in step with it, since
                //that is what the silences table counts down from
                silence.setHours(Duration.between(silence.getStartsat(), silence.getEndsat()).toHours());
            }


            ObjectMapper objectMapper2 = new ObjectMapper();
            objectMapper2.registerModule(new JavaTimeModule());
            objectMapper2.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            String newJson = objectMapper2.writeValueAsString(silence);

            java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder().sslContext(sslContextFactory.getSSLContext()).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(silencesUrl))
                    .POST(HttpRequest.BodyPublishers.ofString(newJson))
                    .header("Content-Type", "application/json")
                    .build();
            java.net.http.HttpResponse<String> response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode()==200) {
                state.addSilence(silence);
                addNote(silence.getId()+":"+System.currentTimeMillis(), silence.getCreatedby(), "Silence submitted to "+state.getAlertmanager(silence.getAlertmanager()).getName());
                return new ResponseEntity<>(new ServiceResponse<>("Silence added"), HttpStatus.OK);
            } else {
                log.warn("Error saving silence: "+response.body());
                log.debug(newJson);
                return new ResponseEntity<>(new ServiceResponse<>("Error saving silence"), HttpStatus.valueOf(response.statusCode()));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean match(String search, String find) {
        Pattern pattern = Pattern.compile(find, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(search);
        return matcher.find();
    }
    
    @PostMapping(value = "/jira", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServiceResponse<Void>> jira(@RequestHeader("CORTANA-TOKEN") String token,
                                                      @RequestBody String payload) {
        try {
            if (!state.isValidSession(token)) {
                log.info("Unauthorized endpoint access: jira");
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }

            //no wraith means no jira in this deployment, which is worth saying
            //plainly rather than failing on a null url further down
            String wraithUrl = wraith.generic();
            if (wraithUrl == null) {
                return new ResponseEntity<>(new ServiceResponse<>("No wraith.base.url configured"), HttpStatus.SERVICE_UNAVAILABLE);
            }

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            WraithGeneric jira = objectMapper.readValue(payload, WraithGeneric.class);


            log.debug("BEFORE:" + jira.toString());
            if (match(jira.getSystem(), "volt")) jira.setEnvironment("HCI VOLT");
            else if (match(jira.getSystem(), "fantasy")) jira.setEnvironment("FANTASY");
            else if (match(jira.getSystem(), "halo")) jira.setEnvironment("HALO");
            else if (match(jira.getSystem(), "place")) jira.setEnvironment("PLACE");
            else if (match(jira.getSystem(), "titan")) jira.setEnvironment("TITAN");
            else if (match(jira.getSystem(), "amp")) jira.setEnvironment("AMP");
            else if (match(jira.getSystem(), "quest")) jira.setEnvironment("QUEST");

            log.debug("AFTER:" + jira.toString());

            String newJson = objectMapper.writeValueAsString(jira);
            log.debug("SAVE JIRA: "+newJson);

            java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder().sslContext(sslContextFactory.getSSLContext()).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(wraithUrl))
                    .POST(HttpRequest.BodyPublishers.ofString(newJson))
                    .header("Content-Type", "application/json")
                    .build();
            java.net.http.HttpResponse<String> response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode()==200) {
                //wraith reports the tickets it made as {"jira_issues_created":["GMDEV-31"]}. The
                //array is empty when it matched an existing unresolved ticket instead of making
                //one, and older wraith builds answer 200 with no body at all.
                String key = firstIssueKey(response.body());
                if (key == null) {
                    log.debug("No jira key in wraith response, nothing to store: " + response.body());
                    return new ResponseEntity<>(new ServiceResponse<>("Jira added with label " + jira.getId()), HttpStatus.OK);
                }
                //wraith only reports tickets it just raised, so this one is open
                if (!storeJiraKey(jira.getId(), key, "open")) {
                    log.warn("Created jira " + key + " but found no alert for " + jira.getId());
                }
                return new ResponseEntity<>(new ServiceResponse<>("Jira " + key + " created"), HttpStatus.OK);
            } else {
                log.warn("Error submitting to jira: "+response.body());
                log.debug(newJson);
                return new ResponseEntity<>(new ServiceResponse<>("Error submitting to jira"), HttpStatus.valueOf(response.statusCode()));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    //link an alert to a jira ticket that already exists, instead of making one.
    //nothing is sent to wraith: the key is simply recorded against the alert.
    @PostMapping(value = "/jira/link", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<ServiceResponse<Void>> jiraLink(@RequestHeader("CORTANA-TOKEN") String token,
                                                          @RequestParam(value = "id")String id,
                                                          @RequestBody String key) {
        try {
            if (!state.isValidSession(token)) {
                log.info("Unauthorized endpoint access: jiraLink");
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }
            String cleaned = (key == null) ? "" : key.trim().toUpperCase();
            //jira keys are PROJECT-123; reject anything else before bothering jira
            if (!cleaned.matches("[A-Z][A-Z0-9_]*-[0-9]+")) {
                return new ResponseEntity<>(new ServiceResponse<>("'" + key + "' is not a jira key like GMDEV-31"), HttpStatus.BAD_REQUEST);
            }
            Optional<AlertManagerEntry> o = logRepo.findById(id);
            if (o.isEmpty()) {
                return new ResponseEntity<>(new ServiceResponse<>("Record not found"), HttpStatus.BAD_REQUEST);
            }
            AlertManagerEntry le = o.get();
            String was = le.getJiraKey();

            //ask jira about the ticket now rather than leaving the status unknown until
            //an ingest gets round to it: a closed ticket linked by hand should look
            //closed straight away, not like live work for the next half minute
            JsonNode issue = readJiraIssue(cleaned);
            String linked = cleaned;
            String status = null;
            if (issue != null) {
                status = WraithTickets.status(issue);
                //a ticket that moved project answers under its new key
                if (WraithTickets.key(issue) != null) { linked = WraithTickets.key(issue); }
            }

            le.setJiraKey(linked);
            le.setJiraStatus(status);
            logRepo.save(le);

            String note = "Jira: linked " + linked + describeTicket(status) + ((was == null) ? "" : " (was " + was + ")");
            addNote(id, state.getUser(token), note);
            return new ResponseEntity<>(new ServiceResponse<>("Jira " + linked + describeTicket(status) + " linked"), HttpStatus.OK);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    //re-point every alert at whatever ticket jira currently labels with its
    //fingerprint, and refresh whether that ticket is open or closed. Wraith answers
    //{"<prefix>:<fingerprint>":[{"key":"GMDEV-53","status":"open"}, ...]} for the
    //labels asked about, and the first open ticket wins, falling back to a closed one.
    //An alert whose label came back with no tickets keeps the link it has unless clear
    //is asked for, since dropping links -- manual ones included -- is not something to
    //do by default.
    @PostMapping(value = "/jira/rebuild")
    public ResponseEntity<ServiceResponse<Void>> jiraRebuild(@RequestHeader("CORTANA-TOKEN") String token,
                                                             @RequestParam(value = "clear", required = false, defaultValue = "false") boolean clear) {
        try {
            //one press rewrites links across every alert in the system
            if (!state.isAdmin(token)) {
                log.info("Unauthorized endpoint access: jiraRebuild");
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }

            String url = wraith.searchLabels();
            if (url == null) {
                return new ResponseEntity<>(new ServiceResponse<>("No wraith.base.url configured"), HttpStatus.SERVICE_UNAVAILABLE);
            }
            //which prefix the tickets carry depends on what raised them, so it is
            //configuration rather than a constant
            String prefix = env.getProperty("jira.label.prefix", "alertmanager");

            List<AlertManagerEntry> entries = logRepo.findAll();
            if (entries.isEmpty()) {
                return new ResponseEntity<>(new ServiceResponse<>("No alerts to rebuild"), HttpStatus.OK);
            }

            ObjectMapper objectMapper = new ObjectMapper();
            List<String> labels = new ArrayList<>();
            for (AlertManagerEntry le : entries) {
                labels.add(prefix + ":" + le.getId());
            }
            String newJson = objectMapper.writeValueAsString(Map.of("labels", labels));
            log.info("Rebuilding jira links for " + labels.size() + " alerts via " + url + (clear ? ", clearing unmatched" : ""));

            java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder().sslContext(sslContextFactory.getSSLContext()).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .POST(HttpRequest.BodyPublishers.ofString(newJson))
                    .header("Content-Type", "application/json")
                    .build();
            java.net.http.HttpResponse<String> response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Error searching jira labels: " + response.body());
                return new ResponseEntity<>(new ServiceResponse<>("Error searching jira"), HttpStatus.valueOf(response.statusCode()));
            }

            JsonNode found = objectMapper.readTree(response.body());
            String user = state.getUser(token);
            int linked = 0;
            int cleared = 0;
            int restatused = 0;
            for (AlertManagerEntry le : entries) {
                JsonNode ticket = WraithTickets.preferred(found, prefix + ":" + le.getId());
                String key = (ticket == null) ? null : WraithTickets.key(ticket);
                String status = (ticket == null) ? null : WraithTickets.status(ticket);
                String was = le.getJiraKey();
                if (was != null && was.isBlank()) { was = null; }
                String wasStatus = le.getJiraStatus();
                //no ticket carries this label. Leave whatever the alert already
                //points at alone unless the caller asked for unmatched links to go
                if (key == null && !clear) { continue; }
                //leave the record alone unless the rebuild actually changes it, so
                //a re-run does not add a note to every alert a second time
                boolean sameKey = (key == null) ? (was == null) : key.equals(was);
                if (sameKey && Objects.equals(status, wasStatus)) { continue; }
                le.setJiraKey(key);
                le.setJiraStatus((key == null) ? null : status);
                logRepo.save(le);
                if (key == null) {
                    cleared++;
                    addNote(le.getId(), user, "Jira: rebuild cleared " + was);
                } else if (sameKey) {
                    //same ticket as before, but it has opened or closed since
                    restatused++;
                    addNote(le.getId(), user, "Jira: " + key + " is now " + ((status == null) ? "of unknown status" : status));
                } else {
                    linked++;
                    addNote(le.getId(), user, "Jira: rebuild linked " + key + ((WraithTickets.CLOSED.equals(status)) ? " (closed)" : "") + ((was == null) ? "" : " (was " + was + ")"));
                }
            }

            log.info("Jira rebuild linked " + linked + ", cleared " + cleared + ", restatused " + restatused);
            String cleanup = clear ? (", " + cleared + " cleared") : "";
            String restated = (restatused > 0) ? (", " + restatused + " status changed") : "";
            return new ResponseEntity<>(new ServiceResponse<>("Jira rebuild: " + linked + " linked" + cleanup + restated + ", of " + entries.size() + " alerts"), HttpStatus.OK);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping(value = "/silence")
    public ResponseEntity<ServiceResponse<Void>> deleteSilence(@RequestHeader("CORTANA-TOKEN") String token,
                                                               @RequestParam(value = "id")String id) {
        try {
            if (!state.isAdmin(token)) {
                log.info("Unauthorized endpoint access: deleteSilence");
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }

            log.info("Delete silence with id "+id);
            Silence silence = state.getSilence(id);
            if (silence==null) {
                log.error("Unable to locate silence "+id+" in state");
            }
            String amName = state.removeSilenceAndGetAlertManager(id);
            String silenceUrl = state.getAlertmanager(amName).getSilenceUrl();

            java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder().sslContext(sslContextFactory.getSSLContext()).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(silenceUrl+"/"+id))
                    .DELETE()
                    .build();
            java.net.http.HttpResponse<Void> response = http.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());


            if (response.statusCode() == 200) {
                return new ResponseEntity<>(new ServiceResponse<>("Record deleted"), HttpStatus.OK);
            } else {
                state.addSilence(silence);
                return new ResponseEntity<>(new ServiceResponse<>("Failed to delete "+id), HttpStatus.NOT_FOUND);
            }

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    //first key of wraith's jira_issues_created, or null if it reported none
    private String firstIssueKey(String body) {
        if (body == null || body.isBlank()) { return null; }
        try {
            JsonNode created = new ObjectMapper().readTree(body).path("jira_issues_created");
            if (created.isArray() && created.size() > 0) {
                String key = created.get(0).asText(null);
                return (key == null || key.isBlank()) ? null : key;
            }
        } catch (Exception e) {
            log.warn("Could not read jira key from wraith response: " + body, e);
        }
        return null;
    }

    //what jira says about one ticket, or null when it could not be asked -- including
    //when this deployment has no wraith behind it, in which case linking carries on
    //with the status unknown and a later refresh fills it in
    private JsonNode readJiraIssue(String key) {
        String url = wraith.getIssue();
        if (url == null) {
            return null;
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            HttpRequest request = HttpRequest.newBuilder().timeout(Duration.ofSeconds(15))
                    .uri(new URI(url))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of("key", key))))
                    .header("Content-Type", "application/json")
                    .build();
            java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                    .sslContext(sslContextFactory.getSSLContext()).build();
            java.net.http.HttpResponse<String> response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Jira lookup of " + key + " answered " + response.statusCode() + ": " + response.body());
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.warn("Could not read jira " + key + " while linking it", e);
            return null;
        }
    }

    //say something about a ticket's state only when it is worth saying: an open one,
    //or one nothing could be learned about, reads better with nothing added
    private String describeTicket(String status) {
        if (WraithTickets.CLOSED.equals(status)) { return " (closed)"; }
        if (WraithTickets.NOT_FOUND.equals(status)) { return " (jira has no such ticket)"; }
        return "";
    }

    //the ticket is labelled "cortana:<fingerprint>" but the alert is stored under the
    //fingerprint alone, so drop the prefix the UI added
    private boolean storeJiraKey(String jiraId, String key, String status) {
        if (jiraId == null) { return false; }
        int colon = jiraId.indexOf(':');
        String id = (colon >= 0) ? jiraId.substring(colon + 1) : jiraId;
        Optional<AlertManagerEntry> o = logRepo.findById(id);
        if (o.isEmpty()) { return false; }
        AlertManagerEntry le = o.get();
        le.setJiraKey(key);
        le.setJiraStatus(status);
        logRepo.save(le);
        return true;
    }

    private boolean addNote(String id, String user, String note) throws Exception {
        Optional<AlertManagerEntry> o = logRepo.findById(id);
        if (o.isPresent()) {
            AlertManagerEntry le = o.get();
            le.addNote(user, note);
            logRepo.save(le);
            return true;
        } else {
            return false;
        }
    }

}
