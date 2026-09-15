package net.njsdomain.alertviewer.data.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A node in the alertmanager routing tree.
 *
 * Values are the EFFECTIVE ones: receiver, groupBy and the three timings cascade
 * from the parent unless the node sets its own, so they are resolved here rather
 * than leaving the UI to walk back up. inherited says which of them came from the
 * parent, so the UI can show "set here" differently from "inherited".
 *
 * The field is continueOnMatch rather than continue, which is a java keyword; the
 * yaml is walked as a map so no annotation is needed to read it.
 */
public class RouteNode {

    private String id;
    private String receiver;
    private List<RouteMatcher> matchers = new ArrayList<>();
    private boolean continueOnMatch;
    private List<String> groupBy = new ArrayList<>();
    private String groupWait;
    private String groupInterval;
    private String repeatInterval;
    private List<String> muteTimeIntervals = new ArrayList<>();
    private List<String> activeTimeIntervals = new ArrayList<>();
    private Map<String, Boolean> inherited = new LinkedHashMap<>();
    private List<RouteNode> routes = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public List<RouteMatcher> getMatchers() { return matchers; }
    public void setMatchers(List<RouteMatcher> matchers) { this.matchers = matchers; }

    public boolean isContinueOnMatch() { return continueOnMatch; }
    public void setContinueOnMatch(boolean continueOnMatch) { this.continueOnMatch = continueOnMatch; }

    public List<String> getGroupBy() { return groupBy; }
    public void setGroupBy(List<String> groupBy) { this.groupBy = groupBy; }

    public String getGroupWait() { return groupWait; }
    public void setGroupWait(String groupWait) { this.groupWait = groupWait; }

    public String getGroupInterval() { return groupInterval; }
    public void setGroupInterval(String groupInterval) { this.groupInterval = groupInterval; }

    public String getRepeatInterval() { return repeatInterval; }
    public void setRepeatInterval(String repeatInterval) { this.repeatInterval = repeatInterval; }

    public List<String> getMuteTimeIntervals() { return muteTimeIntervals; }
    public void setMuteTimeIntervals(List<String> muteTimeIntervals) { this.muteTimeIntervals = muteTimeIntervals; }

    public List<String> getActiveTimeIntervals() { return activeTimeIntervals; }
    public void setActiveTimeIntervals(List<String> activeTimeIntervals) { this.activeTimeIntervals = activeTimeIntervals; }

    public Map<String, Boolean> getInherited() { return inherited; }
    public void setInherited(Map<String, Boolean> inherited) { this.inherited = inherited; }

    public List<RouteNode> getRoutes() { return routes; }
    public void setRoutes(List<RouteNode> routes) { this.routes = routes; }
}
