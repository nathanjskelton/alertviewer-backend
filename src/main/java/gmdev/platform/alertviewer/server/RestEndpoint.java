package gmdev.platform.alertviewer.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import gmdev.platform.alertviewer.data.AlertManagerEntry;
import gmdev.platform.alertviewer.data.AlertManagerEntryRepo;
import gmdev.platform.alertviewer.data.AlertManagerUser;
import gmdev.platform.alertviewer.data.jira.WraithGeneric;
import gmdev.platform.alertviewer.data.silence.Silence;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
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
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

@RestController
public class RestEndpoint {
    private static final Logger log = LoggerFactory.getLogger(RestEndpoint.class);

    @Autowired RequestService requestService;
    @Autowired UserService userService;
    @Autowired MarkService markService;
    @Autowired StateBuffer state;
    @Autowired
    AlertManagerEntryRepo logRepo;
    @Autowired
    Environment env;

    @GetMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServiceResponse<RequestResponse>> login(
            @RequestHeader(value = "Authorization", required = false) String credentials,
            @RequestHeader(value = "CORTANA_DN", required = false) String dn,  
            @RequestHeader(value = "CORTANA_SSL_CERT", required = false) String certString) {
        try {
            String certStringDecoded = URLDecoder.decode( certString, "UTF-8" ); 
            X509Certificate cert = (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(certStringDecoded.getBytes("UTF-8")));
	    String dnFromCert = cert.getSubjectDN().toString().replaceAll("\\s+","");
            log.debug("LOGIN: Authorization "+credentials+" DN "+dnFromCert);
            if (dn == null) {
                if (credentials.startsWith("Basic ")) {
                    String basicCreds = new String(Base64.getDecoder().decode(credentials.substring(6)));
                    dn = basicCreds.split(":")[0];
                    log.debug("Using basic username as DN: "+dn);
                }
            }

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setAccessControlExposeHeaders(List.of("Cortana_token", "Cortana_user", "Cortana_role"));
            Registration reg = state.registerSession(dnFromCert);
            responseHeaders.set("CORTANA_TOKEN", reg.getToken());
            responseHeaders.set("CORTANA_USER", reg.getUser());
            responseHeaders.set("CORTANA_ROLE", reg.getRole());
            return new ResponseEntity("login successful", responseHeaders, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Login error: "+e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.UNAUTHORIZED);
        }
    }

    @GetMapping(value = "/export", produces = MediaType.TEXT_PLAIN_VALUE)
    public String export(@RequestHeader("CORTANA_TOKEN") String token,
                          @RequestParam(required = false,value = "severity")List<String> severity,
                          @RequestParam(required = false,value = "start")String start,
                          @RequestParam(required = false,value = "end")String end,
                          @RequestParam(required = false,value = "statuses")List<String> statuses,
                          @RequestParam(required = false,value = "gminstances")List<String> gminstances,
                          HttpServletResponse response) {

        if (!state.isValidSession(token)) {
            log.info("Unauthorized endpoint access: export");
            return "Unauthorized, please login";
        }

        response.setHeader("Content-Disposition", "attachment; filename=export.txt");
        try {
            return requestService.request(severity, start, end, statuses, gminstances, true).getContent();
        } catch (ServiceException e) {
            log.error("Export error: "+e.getMessage(), e);
            return "ERROR: "+e.getMessage();
        }
    }


    @GetMapping(value = "/alerts", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServiceResponse<RequestResponse>> request(@RequestHeader("CORTANA_TOKEN") String token,
                                    @RequestParam(required = false,value = "severity")List<String> severity,
                                    @RequestParam(required = false,value = "start")String start,
                                    @RequestParam(required = false,value = "end")String end,
                                    @RequestParam(required = false,value = "statuses")List<String> statuses,
                                    @RequestParam(required = false,value = "gminstances")List<String> gminstances) {

        try {
            if (!state.isValidSession(token)) {
                log.info("Unauthorized endpoint access: request");
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }
            log.debug("Query: statuses="+statuses);
            return new ResponseEntity<>(new ServiceResponse<>("Query complete", requestService.request(severity, start, end, statuses, gminstances, false)), HttpStatus.OK);
        } catch (ServiceException e) {
            log.error("Request error: "+e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServiceResponse<UsersResponse>> users(@RequestHeader("CORTANA_TOKEN") String token) {

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
    public ResponseEntity<ServiceResponse<Void>> saveUser(@RequestHeader("CORTANA_TOKEN") String token,
                                                      @RequestBody AlertManagerUser user) {
        if (!state.isAdmin(token)) {
            log.info("Unauthorized endpoint access: save user");
            return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
        }
        userService.saveUser(user);
        return new ResponseEntity<>(new ServiceResponse<>("User added"), HttpStatus.OK);
    }
    @DeleteMapping(value = "/user")
    public ResponseEntity<ServiceResponse<Void>> deleteUser(@RequestHeader("CORTANA_TOKEN") String token,
                                                               @RequestParam(value = "id")String id) {
        if (!state.isAdmin(token)) {
            log.info("Unauthorized endpoint access: delete user");
            return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
        }
        userService.deleteUser(id);
        return new ResponseEntity<>(new ServiceResponse<>("User deleted"), HttpStatus.OK);
    }

    @PutMapping("/mark")
    public ResponseEntity<ServiceResponse<Void>> mark(@RequestHeader("CORTANA_TOKEN") String token,
                      @RequestParam(value = "id")String id,
                      @RequestParam(value = "status")String status) {
        try {
            if (!state.isAdmin(token)) {
                log.info("Unauthorized endpoint access: mark");
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }
            markService.mark(id, status);
            addNote(id, state.getUser(token), "Status set to "+status);
            return new ResponseEntity<>(new ServiceResponse<>("Record "+id+" marked as "+status+" by "+state.getUser(token)), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }



    @GetMapping("/poll")
    public ResponseEntity<ServiceResponse<PollResult>> poll(@RequestHeader("CORTANA_TOKEN") String token) {
        try {
            if (!state.isValidSession(token)) {
                log.info("Unauthorized endpoint access: poll");
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }
            PollResult pr = state.poll(token);
            return new ResponseEntity<>(new ServiceResponse<>("Poll complete", pr), HttpStatus.OK);
        } catch (ServiceException e) {
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @DeleteMapping(value = "/alert")
    public ResponseEntity<ServiceResponse<Void>> delete(@RequestHeader("CORTANA_TOKEN") String token,
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
    public ResponseEntity<ServiceResponse<Void>> note(@RequestHeader("CORTANA_TOKEN") String token,
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

    @PostMapping(value = "/silence", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServiceResponse<Void>> silence(@RequestHeader("CORTANA_TOKEN") String token,
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

            //convert hours to dates and set updated
            LocalDateTime dnow = LocalDateTime.now();
            silence.setStartsat(dnow);
            silence.setUpdatedat(dnow);
            LocalDateTime ends = dnow.plusHours(silence.getHours());
            silence.setEndsat(ends);

            ObjectMapper objectMapper2 = new ObjectMapper();
            objectMapper2.registerModule(new JavaTimeModule());
            objectMapper2.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            String newJson = objectMapper2.writeValueAsString(silence);
            HttpClient http = new DefaultHttpClient();
            HttpPost post = new HttpPost(silencesUrl);
            post.setEntity(new StringEntity(newJson));
            HttpResponse response = http.execute(post);

            if (response.getStatusLine().getStatusCode()==200) {
                state.addSilence(silence);
                return new ResponseEntity<>(new ServiceResponse<>("Silence added"), HttpStatus.OK);
            } else {
                log.warn("Error saving silence: "+response.getStatusLine());
                log.debug(newJson);
                return new ResponseEntity<>(new ServiceResponse<>("Error saving silence"), HttpStatus.valueOf(response.getStatusLine().getStatusCode()));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping(value = "/jira", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServiceResponse<Void>> jira(@RequestHeader("CORTANA_TOKEN") String token,
                                                      @RequestBody String payload) {
        try {
            if (!state.isValidSession(token)) {
                log.info("Unauthorized endpoint access: jira");
                return new ResponseEntity<>(new ServiceResponse<>("Unauthorized, please login"), HttpStatus.UNAUTHORIZED);
            }
            log.debug("SAVE JIRA: "+payload);
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            WraithGeneric jira = objectMapper.readValue(payload, WraithGeneric.class);

            String newJson = objectMapper.writeValueAsString(jira);
            HttpClient http = new DefaultHttpClient();
            HttpPost post = new HttpPost(env.getProperty("wraith.generic.url"));
            post.setHeader("Content-Type", "application/json");
            log.debug("Submitting JSON to Jira: "+newJson);
            post.setEntity(new StringEntity(newJson));
            HttpResponse response = http.execute(post);

            if (response.getStatusLine().getStatusCode()==200) {
                return new ResponseEntity<>(new ServiceResponse<>("Jira added with label " + jira.getId()), HttpStatus.OK);
            } else {
                log.warn("Error submitting to jira: "+response.getStatusLine());
                log.debug(newJson);
                return new ResponseEntity<>(new ServiceResponse<>("Error submitting to jira"), HttpStatus.valueOf(response.getStatusLine().getStatusCode()));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping(value = "/silence")
    public ResponseEntity<ServiceResponse<Void>> deleteSilence(@RequestHeader("CORTANA_TOKEN") String token,
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

            HttpClient http = new DefaultHttpClient();
            HttpDelete delete = new HttpDelete(silenceUrl+"/"+id);
            HttpResponse response = http.execute(delete);

            if (response.getStatusLine().getStatusCode() == 200) {
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
