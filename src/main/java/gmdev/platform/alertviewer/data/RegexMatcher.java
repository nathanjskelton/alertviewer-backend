package gmdev.platform.alertviewer.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class RegexMatcher {

    private static final Logger log = LoggerFactory.getLogger(RegexMatcher.class);

    @Autowired
    RegexRepo regexrepo;

    @Autowired
    AlertManagerRepo logrepo;

    public AlertManagerEntry match(String message) {
        AlertManagerEntry entry = null;

        log.debug("Start Regex Match");
        long start = System.currentTimeMillis();
        List<RegexIndex> regexList = regexrepo.findAllByOrderByLastUsedDesc();
        for (RegexIndex regex:regexList) {
            Pattern p = Pattern.compile(regex.getRegex());
            boolean match = p.matcher(message).matches();
            log.trace("Compare message \""+message+"\" to regex \""+regex.getRegex()+"\"");
            if (match) {
                log.trace("it matched the regex");
                regex.setLastUsed(new Date());
                regexrepo.save(regex);
                entry = logrepo.findById(regex.getLogEntryId()).get();
                break;
            } else {
                log.trace("it DID NOT match the regex");
            }
        }

        log.debug("End Regex Match, Tool "+(System.currentTimeMillis()-start)+"ms");
        return entry;
    }
}
