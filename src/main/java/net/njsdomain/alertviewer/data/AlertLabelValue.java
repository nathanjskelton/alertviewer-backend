package net.njsdomain.alertviewer.data;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * One value a label has carried on some alert, remembered so it outlives the alert.
 *
 * Alerts are transient -- they resolve and are eventually swept -- so the set of teams
 * and environments in play cannot be read off whatever happens to be firing. It has to
 * be accumulated as alerts arrive, which is what this collection is.
 *
 * A value is recorded per alertmanager, not globally: the same team seen on two of
 * them is two entries, because which alertmanager a team appears on is the useful part
 * and a merged list could not answer it. The id is alertmanager:type:value, so
 * recording the same value twice is an upsert rather than a duplicate.
 */
@Document(collection = "AlertLabelValue")
public class AlertLabelValue {

    public static final String TEAM = "team";

    public static final String ENVIRONMENT = "environment";

    @Id
    private String id;

    //the alertmanager this value was seen on. The same value on another one is its
    //own entry, so a list can be asked for per alertmanager or across the lot
    @Indexed
    private String alertmanager;

    //which label this is a value of: team or environment
    @Indexed
    private String type;

    private String value;

    //when this value was first seen, and when an alert last carried it. The second is
    //what tells a stale team apart from one still in use
    private LocalDateTime firstSeen;

    private LocalDateTime lastSeen;

    public static String idFor(String alertmanager, String type, String value) {
        return alertmanager + ":" + type + ":" + value;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAlertmanager() {
        return alertmanager;
    }

    public void setAlertmanager(String alertmanager) {
        this.alertmanager = alertmanager;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public LocalDateTime getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(LocalDateTime firstSeen) {
        this.firstSeen = firstSeen;
    }

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    @Override
    public String toString() {
        return "AlertLabelValue{" + id + '}';
    }
}
