package net.njsdomain.alertviewer.data.routing;

import java.util.ArrayList;
import java.util.List;

/**
 * A receiver and what it actually does. An empty actions list is meaningful, not
 * missing data: alertmanager accepts a receiver with no integrations at all, and
 * anything routed there is silently discarded.
 */
public class ReceiverInfo {

    private String name;
    private boolean used;
    private List<ReceiverAction> actions = new ArrayList<>();

    public ReceiverInfo() {
    }

    public ReceiverInfo(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }

    public List<ReceiverAction> getActions() { return actions; }
    public void setActions(List<ReceiverAction> actions) { this.actions = actions; }
}
