package gmdev.platform.alertviewer.ingest;

import gmdev.platform.alertviewer.data.AlertManagerEntry;
import gmdev.platform.alertviewer.data.AlertManagerEntryRepo;
import gmdev.platform.alertviewer.metrics.MetricsService;
import gmdev.platform.alertviewer.server.StateBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EntryProcessor {

    private static final Logger log = LoggerFactory.getLogger(EntryProcessor.class);

    @Autowired
    MetricsService metrics;

    @Autowired
    AlertManagerEntryRepo logrepo;

    @Autowired
    StateBuffer state;


    public void processIngestedEntry(IngestedEntry input)  {
        /*
        AlertManagerEntry entry = null;

        //first check if this is a true duplicate by id (we've already seen this message) and if so, replace annotations
        log.debug("Try to find record with id: "+input.getEntryId());
        Optional<Occurence> dup = logrepo.findByOccurencesId(input.getEntryId());
        if (dup.isPresent()) {
            //dup.get().getId()
        } else {
            log.debug("Did not find a duplicate");

            //try to find a regex that matches this
            entry = regexMatcher.match(input.getMessage());

            //if there is no match try to find a direct match
            if (entry == null) {
                Optional<AlertManagerEntry> direct = logrepo.findByMessage(input.getMessage());
                if (direct.isPresent()) {
                    entry = direct.get();
                    logMetrics(entry, true);
                }

                //log the regex match
            } else {
                logMetrics(entry, false);
            }

            //if there was a match, use that entry and add an occurrence
            if (entry != null) {
                log.debug("+MATCHED entry: ["+input.getTimestamp().toString() + "]  " +input.getMessage());
                entry.addOccurence(new Occurence(input.getTimestamp(), input.getEntryId()));
                entry.setLastChange(LocalDateTime.now());
            }

            //if there was not a match, create a new entry
            else {
                log.debug("+NEW entry: ["+input.getTimestamp().toString() + "]  " +input.getMessage());
                entry = new AlertManagerEntry(input.getTimestamp(), input.getLogType(), input.getEntryId(), input.getMessage());
                logMetrics(entry, true);
            }

            //save
            state.setStale();
            logrepo.save(entry);

        }
        */
    }

    private void logMetrics(AlertManagerEntry entry, boolean direct) {
        /*
        if ("FATAL".equals(entry.getLogType())) {
            if (LogEntryStatus.NEW.equals(entry.getStatus())) {
                metrics.incNewFatal();
            } else if (LogEntryStatus.WATCH.equals(entry.getStatus())) {
                metrics.incWatchedFatal();
            } else {
                if (direct) metrics.incRepeatFatalDirect(); else metrics.incRepeatFatalRegex();
            }
        } else if ("ERROR".equals(entry.getLogType())) {
            if (LogEntryStatus.NEW.equals(entry.getStatus())) {
                metrics.incNewError();
            } else if (LogEntryStatus.WATCH.equals(entry.getStatus())) {
                metrics.incWatchedError();
            } else {
                if (direct) metrics.incRepeatErrorDirect(); else metrics.incRepeatErrorRegex();
            }
        } else if ("WARN".equals(entry.getLogType())) {
            if (LogEntryStatus.NEW.equals(entry.getStatus())) {
                metrics.incNewWarn();
            } else if (LogEntryStatus.WATCH.equals(entry.getStatus())) {
                metrics.incWatchedWarn();
            } else {
                if (direct) metrics.incRepeatWarnDirect(); else metrics.incRepeatWarnRegex();
            }
        }
        */
    }
}
