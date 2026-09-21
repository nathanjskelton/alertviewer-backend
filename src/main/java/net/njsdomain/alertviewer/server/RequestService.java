package net.njsdomain.alertviewer.server;

import com.mongodb.client.DistinctIterable;
import com.mongodb.client.model.Filters;
import net.njsdomain.alertviewer.data.AlertGroup;
import net.njsdomain.alertviewer.data.AlertHistory;
import net.njsdomain.alertviewer.data.AlertManagerEntry;
import net.njsdomain.alertviewer.util.LogEntryStatus;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class RequestService {

    private static final Logger log = LoggerFactory.getLogger(RequestService.class);
    @Autowired MongoTemplate mongo;
    @Autowired Environment env;

    @Autowired
    StateBuffer state;


    public RequestResponse request(List<String> severity, String start, String end,
                                   List<String> statusStrings, List<String> environments, String groupField, boolean export,
                                   boolean callinOnly)
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

        DistinctIterable<String> instances = mongo.getCollection("AlertManagerEntry").distinct("alert.labels.environment", String.class);
        DistinctIterable<String> severities = mongo.getCollection("AlertManagerEntry").distinct("alert.labels.severity", String.class);

        Bson bson = Filters.exists("alert.labels.environment", false);
        long legacyDocs = mongo.getCollection("AlertManagerEntry").countDocuments(bson);
        log.debug("There are "+legacyDocs+" legacy documents");

        Query query = new Query();
        //if (startDate == null) startDate = LocalDateTime.now().minusDays(1);
        //if (endDate == null) endDate = LocalDateTime.now();

        //build a criteria for each status
        List<Criteria> criteriaOrList = new ArrayList<>();

        Criteria newCriteria = Criteria.where("status").is(LogEntryStatus.NEW);
        if (statusEnums.contains(LogEntryStatus.NEW)) criteriaOrList.add(newCriteria);

        Criteria resolvedCriteria = Criteria.where("status").is(LogEntryStatus.RESOLVED);
        if (statusEnums.contains(LogEntryStatus.RESOLVED)) criteriaOrList.add(resolvedCriteria);

        Criteria silencedCriteria = Criteria.where("status").is(LogEntryStatus.SILENCED);
        if (statusEnums.contains(LogEntryStatus.SILENCED)) criteriaOrList.add(silencedCriteria);


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
        if (environments != null && !environments.isEmpty()) {
            log.debug("anding a environment");
            Criteria environmentsCriteria;
            if (environments.contains("legacy")) {
                environmentsCriteria = new Criteria();
                environmentsCriteria.orOperator(
                        Criteria.where("alert.labels.environment").in(environments),
                        Criteria.where("alert.labels.environment").exists(false)
                );
            } else {
                environmentsCriteria = Criteria.where("alert.labels.environment").in(environments);
            }
            andMe.add(environmentsCriteria);
        }

        Criteria flappingFalse = Criteria.where("flapping").is(false);
        if (!statusEnums.contains(LogEntryStatus.FLAPPING)) andMe.add(flappingFalse);

        //acking an alert takes it out of the view without changing what it is doing,
        //so it filters like an attribute rather than a status. Legacy documents
        //predate the flag and carry no field at all, which reads as not acked
        Criteria notAcked = new Criteria().orOperator(
                Criteria.where("acked").is(false),
                Criteria.where("acked").exists(false));
        if (!statusEnums.contains(LogEntryStatus.ACKED)) andMe.add(notAcked);

        //raising a jira ticket is the other way to take an alert out of the firing
        //view: a team that works from tickets rather than acks gets the same effect
        //from raising one. The two are independent, so an alert can be acked,
        //ticketed, both or neither, and either on its own is enough to hide it
        Criteria noJiraTicket = new Criteria().orOperator(
                Criteria.where("jiraKey").exists(false),
                Criteria.where("jiraKey").is(null),
                Criteria.where("jiraKey").is(""));
        if (!statusEnums.contains(LogEntryStatus.JIRA)) andMe.add(noJiraTicket);

        //callin alerts are the ones whose callin label reads true or 1. Anything
        //else -- absent, empty, false, "no" -- is not a callin. Matched
        //case-insensitively since the label is free text from the alert rules.
        if (callinOnly) {
            log.debug("anding callin");
            andMe.add(Criteria.where("alert.labels.callin").regex("^(true|1)$", "i"));
        }

        Criteria finalCriteria;
        if (!andMe.isEmpty()) {
            log.debug("making a query with ands");
            //copy rather than add to andMe: the history query below reuses it and
            //must not inherit the status clause
            List<Criteria> mainAnds = new ArrayList<>(andMe);
            mainAnds.add(criteria);
            finalCriteria = new Criteria().andOperator(mainAnds.toArray(new Criteria[0]));
        } else {
            log.debug("making a query without ands");
            finalCriteria = criteria;
        }

        query.addCriteria(finalCriteria);
        query.with(Sort.by(Sort.Direction.DESC, "alert.startsAt"));
        log.debug("QUERY: "+query.toString());
        List<AlertManagerEntry> list = mongo.find(query, AlertManagerEntry.class);

        //The timeline graph plots firing history, which is almost entirely
        //resolved alerts, so it would go empty whenever the viewer unticked
        //RESOLVED. Fetch them separately in the cut-down AlertHistory shape,
        //under the same non-status filters the table is using, so the graph
        //keeps its history without the table gaining rows or the response
        //carrying notes and labels nothing reads.
        //
        //Skipped when the caller did ask for RESOLVED, or did not restrict by
        //status at all: either way the full entries above already include them,
        //and returning them twice would double every history bar.
        List<AlertHistory> history = new ArrayList<>();
        if (!criteriaOrList.isEmpty() && !statusEnums.contains(LogEntryStatus.RESOLVED)) {
            List<Criteria> historyAnds = new ArrayList<>(andMe);
            historyAnds.add(resolvedCriteria);
            Query historyQuery = new Query(new Criteria().andOperator(historyAnds.toArray(new Criteria[0])));
            historyQuery.fields()
                    .include("status")
                    .include("alert.startsAt")
                    .include("alert.endsAt")
                    .include("alert.labels.alertname")
                    .include("alert.labels.severity")
                    .include("alert.labels.environment")
                    .include("alert.labels.instance")
                    .include("alert.labels.team")
                    .include("alert.annotations.summary");
            historyQuery.with(Sort.by(Sort.Direction.DESC, "alert.startsAt"));
            log.debug("HISTORY QUERY: "+historyQuery);
            history = mongo.find(historyQuery, AlertHistory.class, "AlertManagerEntry");
            log.debug("history returned "+history.size()+" resolved alerts");
        }
        Set<String> allFields = new TreeSet<>();

        //group by groupfield if specified
        //TODO remove this when receiving multiple fields
        Set<String> groupFields = new LinkedHashSet<>();
        groupFields.add(groupField);

        Map<String, AlertGroup> map = new HashMap<>();
        if (groupField != null && !groupField.isEmpty()) {
            for (AlertManagerEntry alert:list) {
                StringBuilder sb = new StringBuilder();
                String dd = "";
                for (String gf:groupFields) {
                    if (alert.getAlert().getLabels().containsKey(gf)) {
                        sb.append(dd);
                        sb.append(gf + ": " + alert.getAlert().getLabels().get(gf));
                        dd = ", ";
                    } else {
                        sb.append(dd);
                        sb.append(gf + ": UNDEFINED");
                        dd = ", ";
                    }
                    if (!map.containsKey(sb.toString())) map.put(sb.toString(), new AlertGroup());
                    AlertGroup group = map.get(sb.toString());
                    group.add(alert);
                }
            }
        } else {
            map.put("ALL", new AlertGroup(list));
        }

        //get the distinct set of labels for group dropdown
        for (AlertManagerEntry alert:list) {
            allFields.addAll(alert.getAlert().getLabels().keySet());
        }

        List<String> inst = StreamSupport.stream(instances.spliterator(), false)
            .collect(Collectors.toList());
        if (legacyDocs > 0) {
            inst.add("legacy");
        }

        List<String> seve = StreamSupport.stream(severities.spliterator(), false)
                .collect(Collectors.toList());
        return new RequestResponse(map, history, state.getSilences(), inst, seve, state.getAlertmanagerNames(), allFields, export);
    }
}
