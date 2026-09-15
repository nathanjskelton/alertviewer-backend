package net.njsdomain.alertviewer.data.routing;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One thing a receiver does when it fires, flattened out of any *_configs block.
 *
 * sendResolved is deliberately nullable: alertmanager's default differs per
 * integration, so null means "whatever that integration defaults to" and the UI
 * shows nothing rather than asserting a value we did not read.
 */
public class ReceiverAction {

    private String type;
    private String target;
    private Boolean sendResolved;
    private Map<String, String> details = new LinkedHashMap<>();

    public ReceiverAction() {
    }

    public ReceiverAction(String type, String target, Boolean sendResolved) {
        this.type = type;
        this.target = target;
        this.sendResolved = sendResolved;
    }

    public String getType() { return type; }
    public String getTarget() { return target; }
    public Boolean getSendResolved() { return sendResolved; }
    public Map<String, String> getDetails() { return details; }
    public void setDetails(Map<String, String> details) { this.details = details; }
}
