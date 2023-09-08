package gmdev.platform.logviewer.server;

import gmdev.platform.logviewer.data.AlertManagerEntry;
import gmdev.platform.logviewer.data.Occurence;
import gmdev.platform.logviewer.util.LogEntryStatus;
import gmdev.platform.logviewer.util.LogEntryTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class RequestService {

    private static final Logger log = LoggerFactory.getLogger(RequestService.class);
    @Autowired MongoTemplate mongo;
    @Autowired Environment env;




    public RequestResponse request(List<String> types, String start, String end,
                                   List<String> statusStrings, List<String> teamStrings, boolean export)
            throws ServiceException {


        LocalDateTime startDate = null;
        LocalDateTime endDate = null;
        List<LogEntryStatus> statusEnums = new ArrayList<>();
        List<LogEntryTeam> teamEnums = new ArrayList<>();

        try {
            if (start != null && start.length() > 19) {
                startDate = LocalDateTime.parse(start.substring(0, 20));
            } else if (start != null) {
                startDate = LocalDateTime.parse(start);
            }
            if (end != null && end.length() > 19) {
                endDate = LocalDateTime.parse(end.substring(0, 20));
            } else if (end != null) {
                endDate = LocalDateTime.parse(end);
            }
            if (statusStrings != null) {
                for (String s : statusStrings) {
                    statusEnums.add(LogEntryStatus.valueOf(s));
                }
            }
            if (teamStrings != null) {
                for (String s : teamStrings) {
                    teamEnums.add(LogEntryTeam.valueOf(s));
                }
            }
        } catch(Exception e) {
            log.error("unable to parse arguments for request", e);
            throw new ServiceException("Invalid Arguments");
        }


        Query query = new Query();
        if (startDate == null) startDate = LocalDateTime.now().minusDays(1);
        if (endDate == null) endDate = LocalDateTime.now();

        //build the teams or
        List<Criteria> teamCriteria = new ArrayList<>();
        for (LogEntryTeam tm:teamEnums) {
            Criteria c = Criteria.where("teams").in(tm);
            teamCriteria.add(c);
        }

        teamCriteria.add(Criteria.where("teams").is(null));
        teamCriteria.add(Criteria.where("teams").size(0));
        Criteria teamOr = new Criteria().orOperator(teamCriteria.toArray(new Criteria[teamCriteria.size()]));


        //build a criteria for each status
        List<Criteria> criteriaOrList = new ArrayList<>();

        Criteria newCriteria = Criteria.where("status").is(LogEntryStatus.NEW);
        if (statusEnums.contains(LogEntryStatus.NEW)) criteriaOrList.add(newCriteria);

        Criteria ackedCriteria = new Criteria();
        ackedCriteria.andOperator(
                Criteria.where("status").is(LogEntryStatus.ACKED),
                teamOr
        );
        if (statusEnums.contains(LogEntryStatus.ACKED)) criteriaOrList.add(ackedCriteria);

        Criteria resolvedVriteria = new Criteria();
        resolvedVriteria.andOperator(
                Criteria.where("status").is(LogEntryStatus.RESOLVED),
                teamOr
        );
        if (statusEnums.contains(LogEntryStatus.RESOLVED)) criteriaOrList.add(resolvedVriteria);


        /*
        Criteria hideCriteria = new Criteria();
        hideCriteria.andOperator(
                Criteria.where("status").is(LogEntryStatus.HIDE),
                Criteria.where("lastOccurence").gte(startDate),
                Criteria.where("firstOccurence").lte(endDate),
                teamOr
        );
        if (statusEnums.contains(LogEntryStatus.HIDE)) criteriaOrList.add(hideCriteria);
        */


        //combine the appropriate criteria based on selected statuses
        Criteria criteria = new Criteria();
        if (criteriaOrList.size() > 1) {
            criteria.orOperator(criteriaOrList.toArray(new Criteria[criteriaOrList.size()]));
        } else if (criteriaOrList.size() == 1) {
            criteria = criteriaOrList.get(0);
        } else if (criteriaOrList.size() == 0) {
            criteria = Criteria.where("status").exists(true); //TODO was hide
        }



        //add the log type part of the query
        Criteria finalCriteria;
        if (types != null && types.size() > 0) {
            Criteria logType = Criteria.where("alert.labels.severity").in(types);
            finalCriteria = new Criteria().andOperator(criteria, logType);
        } else {
            finalCriteria = criteria;
        }



        query.addCriteria(finalCriteria);
        query.with(Sort.by(Sort.Direction.DESC, "alert.startsAt"));
        log.debug("QUERY: "+query.toString());
        List<AlertManagerEntry> list = mongo.find(query, AlertManagerEntry.class);


        //build URLs
       /*
        final String urlTemplate = env.getProperty("kibana.url");
        if (urlTemplate != null && !urlTemplate.isEmpty()) {

            for (AlertManagerEntry entry: list) {
                for (Occurence occ:entry.getOccurences()) {
                    String url = urlTemplate;
                    url = url.replaceAll("__field__", "_id");
                    url = url.replaceAll("__value__", occ.getId());
                    occ.setUrl(url);
                }
            }


        }

        */
        return new RequestResponse(list, export);
    }
}
