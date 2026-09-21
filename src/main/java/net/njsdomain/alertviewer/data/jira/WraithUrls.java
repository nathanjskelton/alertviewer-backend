package net.njsdomain.alertviewer.data.jira;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Where wraith is, and what it is called there.
 *
 * Only the base url is configuration. The paths underneath it belong to wraith's own
 * api and move with it, so four settings that had to agree with each other became one
 * that cannot be got half right.
 *
 * A blank base means this deployment has no wraith, and therefore no jira: every
 * accessor answers null and each jira feature reads that as "stay out of the way".
 */
@Component
public class WraithUrls {

    @Autowired
    Environment env;

    /** Raises a ticket. */
    public String generic() {
        return url("/generic");
    }

    /** Every ticket carrying each of a set of labels. */
    public String searchLabels() {
        return url("/search_labels");
    }

    /** The state of each of a set of tickets, by key. */
    public String getIssues() {
        return url("/get_issues");
    }

    /** The state of one ticket, by key. */
    public String getIssue() {
        return url("/get_issue");
    }

    /** Whether there is a wraith at all, and so whether jira is in play here. */
    public boolean enabled() {
        return base() != null;
    }

    private String base() {
        String base = env.getProperty("wraith.base.url");
        if (base == null || base.isBlank()) {
            return null;
        }
        //a trailing slash would double up against the paths below
        return base.trim().replaceAll("/+$", "");
    }

    private String url(String path) {
        String base = base();
        return (base == null) ? null : base + path;
    }
}
