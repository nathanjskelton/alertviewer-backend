package gmdev.platform.logviewer.server;

import gmdev.platform.logviewer.data.AlertManagerEntry;
import gmdev.platform.logviewer.data.AlertManagerRepo;
import gmdev.platform.logviewer.data.RegexRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@RestController
public class RestEndpoint {
    private static final Logger log = LoggerFactory.getLogger(RestEndpoint.class);

    @Autowired RequestService requestService;
    @Autowired MarkService markService;
    @Autowired CombineService combineService;
    @Autowired StateBuffer state;
    @Autowired MergeAllAsync mergeAllAsync;
    @Autowired
    AlertManagerRepo logRepo;
    @Autowired
    RegexRepo regexRepo;


    @GetMapping(value = "/export", produces = MediaType.TEXT_PLAIN_VALUE)
    public String export(@RequestParam(required = false,value = "severity")List<String> severity,
                          @RequestParam(required = false,value = "start")String start,
                          @RequestParam(required = false,value = "end")String end,
                          @RequestParam(required = false,value = "statuses")List<String> statuses,
                          @RequestParam(required = false,value = "gminstances")List<String> gminstances,
                          HttpServletResponse response) {

        response.setHeader("Content-Disposition", "attachment; filename=export.txt");
        try {
            return requestService.request(severity, start, end, statuses, gminstances, true).getContent();
        } catch (ServiceException e) {
            log.error("Export error: "+e.getMessage(), e);
            return "ERROR: "+e.getMessage();
        }
    }


    @GetMapping(value = "/request", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServiceResponse<RequestResponse>> request(@RequestParam(required = false,value = "severity")List<String> severity,
                                                                            @RequestParam(required = false,value = "start")String start,
                                                                            @RequestParam(required = false,value = "end")String end,
                                                                            @RequestParam(required = false,value = "statuses")List<String> statuses,
                                                                            @RequestParam(required = false,value = "gminstances")List<String> gminstances) {

        try {
            return new ResponseEntity<>(new ServiceResponse<>("Query complete", requestService.request(severity, start, end, statuses, gminstances, false)), HttpStatus.OK);
        } catch (ServiceException e) {
            log.error("Request error: "+e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @PutMapping("/mark")
    public ResponseEntity<ServiceResponse<Void>> mark(@RequestParam(value = "id")String id, @RequestParam(value = "status")String status) {
        try {
            markService.mark(id, status);
            addNote(id, "system", "Status set to "+status);
            return new ResponseEntity<>(new ServiceResponse<>("Record "+id+" marked as "+status), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @PutMapping("/team")
    public ResponseEntity<ServiceResponse<Void>> setTeams(@RequestParam(value = "id")String id, @RequestParam(value = "teams")List<String> teams) {
        try {
            markService.teams(id, teams);
            addNote(id, "system", "Teams set to "+teams);
            return new ResponseEntity<>(new ServiceResponse<>("Record "+id+" teams set to  "+teams), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value = "/suggestCombine", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> suggestCombine(@RequestParam(value = "ids")List<String> ids) {
        try {
            CombineSuggestion suggestion = (combineService.suggestCombine(ids));
            if (suggestion.getExistingId() != null) {
                log.debug("Matching REGEX already exists, use merge: "+ suggestion.getSuggestion());
                return new ResponseEntity<>("Matching REGEX already exists, use merge:"+ suggestion.getSuggestion(), HttpStatus.BAD_REQUEST);
            }
            log.debug("REGEX from controller: "+suggestion);
            return new ResponseEntity<>(suggestion.getSuggestion(), HttpStatus.OK);
        } catch (ServiceException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping(value = "/applyCombine", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<ServiceResponse<Void>> applyCombine(@RequestParam(value = "ids")List<String> ids, @RequestBody String regex) {
        try {

            combineService.applyCombine(ids, regex);

            return new ResponseEntity<>(new ServiceResponse<>("Apply complete, records combined"), HttpStatus.OK);
        } catch (ServiceException e) {
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/merge")
    public ResponseEntity<ServiceResponse<Void>> merge(@RequestParam(value = "ids")List<String> ids) {
        try {
            int count = combineService.merge(ids);
            return new ResponseEntity<>(new ServiceResponse<>("Merged "+count+" records"), HttpStatus.OK);
        } catch (ServiceException e) {
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/poll")
    public ResponseEntity<ServiceResponse<PollResult>> poll(@RequestParam(value = "sessionId")String sessionId, HttpServletRequest request) {
        try {
            if (sessionId == null || sessionId.isEmpty() || sessionId.equals("null")) {
                sessionId = request.getSession().getId();
                log.debug("New session created: "+sessionId);
            }
            PollResult pr = state.poll(sessionId);
            return new ResponseEntity<>(new ServiceResponse<>("Poll complete", pr), HttpStatus.OK);
        } catch (ServiceException e) {
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/mergeAll")
    public ResponseEntity<ServiceResponse<Void>> mergeAll() {
        try {
            CompletableFuture<String> future = mergeAllAsync.mergeAll();
            state.addAsync("MergeAll",future);
            return new ResponseEntity<>(new ServiceResponse<>("Merge all started in background"), HttpStatus.OK);
        } catch (ServiceException e) {
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping(value = "/update", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<ServiceResponse<Void>> update(@RequestParam(value = "id")String id, @RequestBody String regex) {
        try {

            combineService.updateRegex(id, regex);

            return new ResponseEntity<>(new ServiceResponse<>("Update complete"), HttpStatus.OK);
        } catch (ServiceException e) {
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping(value = "/delete")
    public ResponseEntity<ServiceResponse<Void>> delete(@RequestParam(value = "id")String id) {
        try {

            logRepo.deleteById(id);
            regexRepo.deleteByLogEntryId(id);

            return new ResponseEntity<>(new ServiceResponse<>("Record deleted"), HttpStatus.OK);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping(value = "/resetCount")
    public ResponseEntity<ServiceResponse<Void>> resetCount(@RequestParam(value = "id")String id) {
        try {

            Optional<AlertManagerEntry> o = logRepo.findById(id);
            if (o.isPresent()) {
                AlertManagerEntry le = o.get();
                //le.setTotalOccurences(0);
                le.addNote("user", "Count reset");
                logRepo.save(le);
                return new ResponseEntity<>(new ServiceResponse<>("Record count reset"), HttpStatus.OK);
            }
            return new ResponseEntity<>(new ServiceResponse<>("Record not found"), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(new ServiceResponse<>(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @PostMapping(value = "/note", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<ServiceResponse<Void>> note(@RequestParam(value = "id")String id, @RequestBody String note) {
        try {

            boolean added = addNote(id, "user", note);
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

    private boolean addNote(String id, String user, String note) throws Exception {
        Optional<AlertManagerEntry> o = logRepo.findById(id);
        if (o.isPresent()) {
            AlertManagerEntry le = o.get();
            le.addNote("user", note);
            logRepo.save(le);
            return true;
        } else {
            return false;
        }
    }

}
