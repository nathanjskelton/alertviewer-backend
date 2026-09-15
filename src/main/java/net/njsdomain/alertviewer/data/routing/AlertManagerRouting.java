package net.njsdomain.alertviewer.data.routing;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

/**
 * The routing configuration of one alertmanager, read from its live status api.
 *
 * When a refresh fails the previous tree is kept and stale is set, so a blip does
 * not blank the screen; available is false only when nothing has ever been read.
 */
public class AlertManagerRouting {

    private String name;
    private boolean available;
    private boolean stale;
    private String error;
    private long fetchedAt;
    private RouteNode route;
    private List<ReceiverInfo> receivers = new ArrayList<>();
    private List<String> timeIntervals = new ArrayList<>();

    //used only to skip reparsing an unchanged config; never sent to the ui
    @JsonIgnore
    private String configHash;

    public AlertManagerRouting() {
    }

    public AlertManagerRouting(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public boolean isStale() { return stale; }
    public void setStale(boolean stale) { this.stale = stale; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public long getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(long fetchedAt) { this.fetchedAt = fetchedAt; }

    public RouteNode getRoute() { return route; }
    public void setRoute(RouteNode route) { this.route = route; }

    public List<ReceiverInfo> getReceivers() { return receivers; }
    public void setReceivers(List<ReceiverInfo> receivers) { this.receivers = receivers; }

    public List<String> getTimeIntervals() { return timeIntervals; }
    public void setTimeIntervals(List<String> timeIntervals) { this.timeIntervals = timeIntervals; }

    public String getConfigHash() { return configHash; }
    public void setConfigHash(String configHash) { this.configHash = configHash; }
}
