package gmdev.platform.alertviewer.server;

import gmdev.platform.alertviewer.data.AlertManagerEntryRepo;
import gmdev.platform.alertviewer.util.LogEntryStatus;
import gmdev.platform.alertviewer.util.LogEntryTeam;
import gmdev.platform.alertviewer.data.AlertManagerEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class MarkService {

    private static final Logger log = LoggerFactory.getLogger(MarkService.class);
    @Autowired
    AlertManagerEntryRepo repo;

    public void mark(String id, String status) throws ServiceException {
        LogEntryStatus ls = LogEntryStatus.valueOf(status);
        log.debug("Mark "+id+" as "+ls.toString());
        Optional<AlertManagerEntry> entry = repo.findById(id);
        if (entry.isPresent()) {
            AlertManagerEntry le = entry.get();
            le.setStatus(LogEntryStatus.valueOf(status));
            repo.save(le);
        } else {
            throw new ServiceException("Record not found, unable to mark");
        }
    }

    public void teams(String id, List<String> teams) throws ServiceException {
        log.debug("Set teams on "+id+" to "+teams.toString());
        List<LogEntryTeam> teamsEnum = new ArrayList<>();
        for (String team:teams) {
            teamsEnum.add(LogEntryTeam.valueOf(team));
        }
        Optional<AlertManagerEntry> entry = repo.findById(id);
        if (entry.isPresent()) {
            AlertManagerEntry le = entry.get();
            //le.setTeams(teamsEnum);
            repo.save(le);
        } else {
            throw new ServiceException("Record not found, unable to mark teams");
        }
    }
}
