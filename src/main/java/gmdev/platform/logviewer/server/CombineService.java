package gmdev.platform.logviewer.server;

import gmdev.platform.logviewer.data.*;
import gmdev.platform.logviewer.util.LogEntryStatus;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class CombineService {

    private static final Logger log = LoggerFactory.getLogger(CombineService.class);

    @Autowired
    AlertManagerRepo repo;
    @Autowired
    RegexRepo regexrepo;
    @Autowired
    RegexMatcher regexMatcher;
    @Autowired StateBuffer state;

    public CombineSuggestion suggestCombine(List<String> ids) throws ServiceException {
        /*
        log.info("Generating suggested regex...");
        if (!state.aquireLock()) {
            throw new ServiceException("Lock enabled, try again later");
        }
        try {
            String existingId = null;
            String existingRegex = null;
            List<String> strings = new ArrayList<>();
            for (String id : ids) {
                Optional<AlertManagerEntry> log = repo.findById(id);
                if (log.isPresent()) {
                    AlertManagerEntry matched = regexMatcher.match(log.get().getMessage());
                    if (matched != null) {
                        if (existingId != null && !existingId.equals(matched.getId())) {
                            state.releaseLock();
                            throw new ServiceException("This set of messages match more than one existing regex, cannot merge");
                        }
                        existingId = matched.getId();
                        Optional<RegexIndex> index = regexrepo.findByLogEntryId(existingId);
                        if (index.isPresent()) {
                            existingRegex = index.get().getRegex();
                        } else {
                            state.releaseLock();
                            throw new ServiceException("Regex not indexed for regex LogEntry " + existingId);
                        }
                    }
                    strings.add(log.get().getMessage());
                } else {
                    state.releaseLock();
                    throw new ServiceException("Unable to load all specified log messages");
                }
            }

            if (existingId != null) {
                state.releaseLock();
                return new CombineSuggestion(existingRegex, existingId);
            } else {
                state.releaseLock();
                return new CombineSuggestion(calculateRegex(strings));
            }
        } finally {
            state.releaseLock();
        }

        */
        return null;
    }

    public void updateRegex(String id, String regex) throws ServiceException {
        /*
        if (!state.aquireLock()) {
            throw new ServiceException("Lock enabled, try again later");
        }
        Optional<AlertManagerEntry> le = repo.findById(id);
        if (le.isPresent()) {
            AlertManagerEntry entry = le.get();
            if (entry.isRegex()) {
                entry.setMessage(regex);

                Optional<RegexIndex> reio = regexrepo.findByLogEntryId(entry.getId());
                if (reio.isPresent()) {
                    RegexIndex rei = reio.get();
                    rei.setRegex(regex);
                    repo.save(entry);
                    regexrepo.save(rei);
                } else {
                    state.releaseLock();
                    throw new ServiceException("RegexIndex for LogEntry "+id+" not found.");
                }
            } else {
                state.releaseLock();
                throw new ServiceException("LogEntry "+id+" is not a regex record. Use combine first.");
            }
        } else {
            state.releaseLock();
            throw new ServiceException("LogEntry "+id+" not found.");
        }
        state.releaseLock();
         */
    }

    public void applyCombine(List<String> ids, String regex) throws ServiceException {
        /*
        if (!state.aquireLock()) {
            throw new ServiceException("Lock enabled, try again later");
        }

        try {
            log.info("Combining messages...");
            Pattern p = Pattern.compile(regex);

            //verify each string against regex, then combine into 1 record
            AlertManagerEntry entry = null;
            boolean first = true;
            List<AlertManagerEntry> deleteme = new ArrayList<>();
            for (String id : ids) {
                Optional<AlertManagerEntry> le = repo.findById(id);
                if (le.isPresent()) {
                    if (first) {
                        entry = le.get();
                        if (!p.matcher(entry.getMessage()).matches()) {
                            log.debug("Regex '" + regex + "' does not match '" + entry.getMessage() + "'");
                            state.releaseLock();
                            throw new ServiceException("Pattern does not match entry with id " + id);
                        }
                        entry.setMessage(regex);
                        entry.setRegex(true);
                        first = false;
                    } else {
                        AlertManagerEntry addme = le.get();
                        if (!p.matcher(addme.getMessage()).matches()) {
                            log.debug("Regex '" + regex + "' does not match '" + addme.getMessage() + "'");
                            state.releaseLock();
                            throw new ServiceException("Pattern does not match entry with id " + id);
                        }
                        combineEntries(entry, addme);
                        deleteme.add(addme);
                    }
                }
            }

            //save the regex into an index with the ID of this record
            regexrepo.save(new RegexIndex(entry.getId(), regex));

            //save the combined entry
            repo.save(entry);

            //remove the old entries
            repo.deleteAll(deleteme);

            state.releaseLock();
        } finally {
            state.releaseLock();
        }


         */
    }

    private void combineEntries(AlertManagerEntry target, AlertManagerEntry source) {
        /*
        target.getNotes().addAll(source.getNotes());
        target.setStatus(LogEntryStatus.NEW);
        target.setLastChange(LocalDateTime.now());
        //TODO entry.setLastUser(user);
        target.getOccurences().addAll(source.getOccurences());
        target.setTotalOccurences(target.getTotalOccurences() + source.getTotalOccurences());
        target.truncateOccurences();
        if (source.getFirstOccurence().isBefore(target.getFirstOccurence())) {
            target.setFirstOccurence(source.getFirstOccurence());
        }
        if (source.getLastOccurence().isAfter(target.getLastOccurence())) {
            target.setLastOccurence(source.getLastOccurence());
        }

         */
    }


    public int merge(List<String> ids) throws ServiceException {
        /*
        if (!state.aquireLock()) {
            throw new ServiceException("Lock enabled, try again later");
        }
        try {
            int count = 0;
            for (String id : ids) {
                log.debug("Merging " + id + "...");
                Optional<AlertManagerEntry> le = repo.findById(id);
                if (le.isPresent()) {
                    AlertManagerEntry entryToMatch = le.get();
                    AlertManagerEntry matchedEntry = regexMatcher.match(entryToMatch.getMessage());

                    if (matchedEntry != null) {
                        combineEntries(matchedEntry, entryToMatch);
                        repo.save(matchedEntry);
                        repo.delete(entryToMatch);
                        log.debug("Merged " + matchedEntry.getId() + " with " + entryToMatch.getId());
                        count++;
                    }
                }
            }
            state.releaseLock();
            return count;
        } finally {
            state.releaseLock();
        }

         */
        return 0;
    }


    private String calculateRegex(List<String> input) throws ServiceException {
        String[] originalStrings = input.toArray(new String[input.size()]);
        String[] strings = input.toArray(new String[input.size()]);

        StringBuilder common = new StringBuilder();
        boolean delimAdded = false;
        boolean cr = false;
        if (strings[0].contains("\n")) {
            cr = true;
        }

        main: while(true) {
            //System.out.println("");
            log.trace(strings[0]);

            //look for a difference
            int dif = StringUtils.indexOfDifference(strings);
            log.trace("...found a dif at "+dif);

            //rewind to last delimiter
            dif = getPreviousDelimiter(dif, strings[0]);
            log.trace("...set dif to "+dif);

            //if there are no more differences, add the rest to the common and stop
            if (dif < 0) {
                common.append(strings[0]);
                log.debug("No more differences: "+dif);
                break main;
            }

            //save off the common part and add a .*
            if (dif > 0) {
                String newText = strings[0].substring(0, dif);
                if (newText.trim().isEmpty() && delimAdded) {
                    log.trace("...skip redundant wildcard");
                } else {
                    common.append(newText);
                    common.append("___wc___");
                    delimAdded = true;
                }
            }

            //skip to next delimiter (space) on each string
            for (int i = 0; i < strings.length; i++) {
                int spc = strings[i].indexOf(" ", dif);
                log.trace("...next space is at "+spc);
                if (spc > -1) {
                    strings[i] = strings[i].substring(spc);
                } else {
                    log.trace("...no more delimiters, ending");
                    break main;
                }
            }
        }

        //escape special characters
        String regex = common.toString().replace("\\", "\\\\");
        regex = regex.replace(".", "\\.");
        regex = regex.replace("*", "\\*");
        regex = regex.replace("___wc___", ".*");
        regex = regex.replace("^", "\\^");
        regex = regex.replace("$", "\\$");
        regex = regex.replace("+", "\\+");
        regex = regex.replace("?", "\\?");
        regex = regex.replace("(", "\\(");
        regex = regex.replace(")", "\\)");
        regex = regex.replace("[", "\\[");
        regex = regex.replace("{", "\\{");
        regex = regex.replace("|", "\\|");

        if (cr) regex = "(?s)"+regex;

        //done
        log.debug("REGEX: "+regex);

        //check the strings
        boolean allMatch = true;
        Pattern p = Pattern.compile(regex);
        for (int i = 0; i < originalStrings.length; i++) {
            if (!p.matcher(originalStrings[i]).matches()) {
                allMatch = false;
                log.debug(originalStrings[i]+" does not match regex");
            }

        }
        if (allMatch) {
            return regex;
        }
        ServiceException e = new ServiceException("Generated regex did not match all log messages");
        log.error(e.getMessage(), e);
        throw e;
    }

    private int getPreviousDelimiter(int dif, String string) {
        int p = dif - 1;
        while (p >= 0) {
            if (string.substring(p, p+1).equals(" ")) {
                return p+1;
            }
            p--;
        }
        return dif;
    }

}
