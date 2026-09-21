package net.njsdomain.alertviewer.server;

import net.njsdomain.alertviewer.data.AlertManagerEntryRepo;
import net.njsdomain.alertviewer.util.LogEntryStatus;
import net.njsdomain.alertviewer.util.LogEntryTeam;
import net.njsdomain.alertviewer.data.AlertManagerEntry;
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

    /**
     * Apply a mark to an entry, and report what was done so the caller can note it.
     *
     * ACKED and UNACKED move the ack flag and leave the status alone, so an alert can
     * be acked whatever it is doing -- firing, silenced or long since resolved -- and
     * unacking never has to guess which status to put back. Anything else is a status.
     */
    public String mark(String id, String status) throws ServiceException {
        LogEntryStatus ls = LogEntryStatus.valueOf(status);
        log.debug("Mark "+id+" as "+ls.toString());
        Optional<AlertManagerEntry> entry = repo.findById(id);
        if (entry.isEmpty()) {
            throw new ServiceException("Record not found, unable to mark");
        }

        AlertManagerEntry le = entry.get();
        if (LogEntryStatus.ACKED.equals(ls) || LogEntryStatus.UNACKED.equals(ls)) {
            boolean acked = LogEntryStatus.ACKED.equals(ls);
            le.setAcked(acked);
            //an acked alert is one somebody is holding, so stop calling it flappy
            if (acked) { le.setFlapping(false); }
            repo.save(le);
            return acked ? "Acked" : "Unacked";
        }

        le.setStatus(ls);
        repo.save(le);
        return "Status set to "+status;
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
