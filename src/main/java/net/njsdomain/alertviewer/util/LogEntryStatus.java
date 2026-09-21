package net.njsdomain.alertviewer.util;

public enum LogEntryStatus {
    //an entry's status is one of NEW, RESOLVED, SILENCED or INHIBITED. The rest are
    //never stored: they name an attribute of an entry rather than its state, and
    //exist so a filter or a mark can be asked for by name. Whether an alert is acked
    //lives in its own flag, exactly as whether it has a jira ticket lives in its key,
    //so an alert can be acked and firing, or acked and resolved
    NEW, RESOLVED, SILENCED, INHIBITED, ACKED, UNACKED, FLAPPING, JIRA
}