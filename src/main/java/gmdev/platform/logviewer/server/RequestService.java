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




    public RequestResponse request(List<String> severity, String start, String end,
                                   List<String> statusStrings, List<String> gminstances, boolean export)
            throws ServiceException {

        log.debug("start="+start+", end="+end);

        LocalDateTime startDate = null;
        LocalDateTime endDate = null;
        List<LogEntryStatus> statusEnums = new ArrayList<>();

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

        } catch(Exception e) {
            log.error("unable to parse arguments for request", e);
            throw new ServiceException("Invalid Arguments");
        }

        log.debug("startDate="+startDate+", endDate="+endDate);

        Query query = new Query();
        //if (startDate == null) startDate = LocalDateTime.now().minusDays(1);
        //if (endDate == null) endDate = LocalDateTime.now();

        //build a criteria for each status
        List<Criteria> criteriaOrList = new ArrayList<>();

        Criteria newCriteria = Criteria.where("status").is(LogEntryStatus.NEW);
        if (statusEnums.contains(LogEntryStatus.NEW)) criteriaOrList.add(newCriteria);

        Criteria ackedCriteria = Criteria.where("status").is(LogEntryStatus.ACKED);
        if (statusEnums.contains(LogEntryStatus.ACKED)) criteriaOrList.add(ackedCriteria);

        Criteria resolvedCriteria = Criteria.where("status").is(LogEntryStatus.RESOLVED);
        if (statusEnums.contains(LogEntryStatus.RESOLVED)) criteriaOrList.add(resolvedCriteria);


        //combine the appropriate criteria based on selected statuses
        Criteria criteria = new Criteria();
        if (criteriaOrList.size() > 1) {
            criteria.orOperator(criteriaOrList.toArray(new Criteria[criteriaOrList.size()]));
        } else if (criteriaOrList.size() == 1) {
            criteria = criteriaOrList.get(0);
        } else {
            criteria = Criteria.where("status").exists(true); //TODO was hide
        }


        //add the log severity part of the query
        List<Criteria> andMe = new ArrayList<>();
        if (startDate != null) {
            log.debug("anding a startDate");
            Criteria startDateCriteria = Criteria.where("alert.startsAt").gte(startDate);
            andMe.add(startDateCriteria);
        }
        if (endDate != null) {
            log.debug("anding a endDate");
            Criteria endDateCriteria = Criteria.where("alert.endsAt").lte(endDate);
            andMe.add(endDateCriteria);
        }
        if (severity != null && !severity.isEmpty()) {
            log.debug("anding a severity");
            Criteria severityCriteria = Criteria.where("alert.labels.severity").in(severity);
            andMe.add(severityCriteria);
        }
        if (gminstances != null && !gminstances.isEmpty()) {
            log.debug("anding a gm_instance");
            Criteria gmInstancesCriteria = Criteria.where("alert.labels.gm_instance").in(gminstances);
            andMe.add(gmInstancesCriteria);
        }

        Criteria finalCriteria;
        if (!andMe.isEmpty()) {
            log.debug("making a query with ands");
            andMe.add(criteria);
            finalCriteria = new Criteria().andOperator(andMe.toArray(new Criteria[0]));
        } else {
            log.debug("making a query without ands");
            finalCriteria = criteria;
        }

        query.addCriteria(finalCriteria);
        query.with(Sort.by(Sort.Direction.DESC, "alert.startsAt"));
        log.debug("QUERY: "+query.toString());
        List<AlertManagerEntry> list = mongo.find(query, AlertManagerEntry.class);

        return new RequestResponse(list, export);
    }
}
