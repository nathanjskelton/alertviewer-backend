package net.njsdomain.alertviewer.data.routing;

/**
 * One criterion on a route, normalised from any of the three forms alertmanager
 * accepts: the modern matchers list ({@code jira="true"}), legacy match (=) and
 * legacy match_re (=~). raw is kept so the UI can fall back to alertmanager's own
 * text when a matcher is too exotic to split.
 */
public class RouteMatcher {

    private String name;
    private String op;
    private String value;
    private String raw;

    public RouteMatcher() {
    }

    public RouteMatcher(String name, String op, String value, String raw) {
        this.name = name;
        this.op = op;
        this.value = value;
        this.raw = raw;
    }

    public String getName() { return name; }
    public String getOp() { return op; }
    public String getValue() { return value; }
    public String getRaw() { return raw; }

    @Override
    public String toString() {
        return (raw != null) ? raw : (name + op + value);
    }
}
