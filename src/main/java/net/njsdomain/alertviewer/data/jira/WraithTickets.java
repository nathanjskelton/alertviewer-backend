package net.njsdomain.alertviewer.data.jira;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Reading wraith's two answers about jira tickets.
 *
 * The label search answers
 * {"<prefix>:<fingerprint>":[{"key":"GMDEV-53","status":"open"}, ...]} for the labels
 * it was asked about. A label can carry several tickets over an alert's life, so
 * something has to choose between them, and an open ticket always wins over a closed
 * one. The key lookup answers a single {"key":..., "status":...} for one ticket, and
 * reports "notfound" for a key jira no longer has; the key readers below take either
 * shape, since a lone ticket is just a list of one.
 */
public final class WraithTickets {

    public static final String CLOSED = "closed";

    public static final String NOT_FOUND = "notfound";

    private WraithTickets() {}

    /**
     * The ticket the alert should follow: the first open one, or the first closed one
     * when the label has nothing open left. Null when the label listed no usable ticket.
     */
    public static JsonNode preferred(JsonNode found, String label) {
        JsonNode tickets = found.path(label);
        if (!tickets.isArray() || tickets.size() == 0) { return null; }
        JsonNode closed = null;
        for (JsonNode ticket : tickets) {
            if (key(ticket) == null) { continue; }
            if (!isClosed(ticket)) { return ticket; }
            if (closed == null) { closed = ticket; }
        }
        return closed;
    }

    /**
     * The key out of one of wraith's ticket entries, or null when it carries none.
     * Wraith builds from before the status change listed bare keys, not objects.
     */
    public static String key(JsonNode ticket) {
        String key = ticket.isTextual() ? ticket.asText(null) : ticket.path("key").asText(null);
        return (key == null || key.isBlank()) ? null : key.trim().toUpperCase();
    }

    /** "open", "closed", or null when wraith did not say. */
    public static String status(JsonNode ticket) {
        String status = ticket.path("status").asText(null);
        return (status == null || status.isBlank()) ? null : status.trim().toLowerCase();
    }

    /** Closed only when jira said so: an unknown status counts as open. */
    public static boolean isClosed(JsonNode ticket) {
        return CLOSED.equals(status(ticket));
    }

    /** Jira has no such ticket any more, so there is no work left to wait on. */
    public static boolean isNotFound(JsonNode ticket) {
        return NOT_FOUND.equals(status(ticket));
    }
}
