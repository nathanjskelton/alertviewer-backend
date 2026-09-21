package net.njsdomain.alertviewer.data.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wraith lists every ticket carrying a label, open and closed alike, so something has
 * to choose which one an alert follows. An open ticket always wins, and both the
 * rebuild and the resolved-alert expiry lean on that.
 */
class WraithTicketsTest {

    private static final String LABEL = "alertmanager:1a30ba71cca2921f";

    private JsonNode chosen(String json) throws Exception {
        return WraithTickets.preferred(new ObjectMapper().readTree(json), LABEL);
    }

    private String chosenKey(String json) throws Exception {
        JsonNode ticket = chosen(json);
        return (ticket == null) ? null : WraithTickets.key(ticket);
    }

    @Test
    void takesTheFirstOpenTicketEvenWhenAClosedOneIsListedFirst() throws Exception {
        String json = "{\"" + LABEL + "\":[{\"key\":\"GMDEV-50\",\"status\":\"closed\"}," +
                "{\"key\":\"GMDEV-52\",\"status\":\"open\"}," +
                "{\"key\":\"GMDEV-53\",\"status\":\"open\"}]}";
        assertEquals("GMDEV-52", chosenKey(json));
        assertEquals("open", WraithTickets.status(chosen(json)));
        assertFalse(WraithTickets.isClosed(chosen(json)));
    }

    @Test
    void fallsBackToTheFirstClosedTicketWhenNothingIsOpen() throws Exception {
        String json = "{\"" + LABEL + "\":[{\"key\":\"GMDEV-50\",\"status\":\"closed\"}," +
                "{\"key\":\"GMDEV-49\",\"status\":\"closed\"}]}";
        assertEquals("GMDEV-50", chosenKey(json));
        assertTrue(WraithTickets.isClosed(chosen(json)));
    }

    @Test
    void aTicketWraithGaveNoStatusForCountsAsOpen() throws Exception {
        String json = "{\"" + LABEL + "\":[{\"key\":\"GMDEV-50\",\"status\":\"closed\"},{\"key\":\"GMDEV-52\"}]}";
        assertEquals("GMDEV-52", chosenKey(json));
        assertNull(WraithTickets.status(chosen(json)));
        assertFalse(WraithTickets.isClosed(chosen(json)));
    }

    @Test
    void readsTheBareKeyStringsOlderWraithBuildsReturn() throws Exception {
        String json = "{\"" + LABEL + "\":[\"gmdev-52\",\"GMDEV-50\"]}";
        assertEquals("GMDEV-52", chosenKey(json));
        assertFalse(WraithTickets.isClosed(chosen(json)));
    }

    @Test
    void readsTheSingleTicketTheKeyLookupAnswersWith() throws Exception {
        JsonNode open = new ObjectMapper().readTree("{\"key\":\"GMDEV-52\",\"status\":\"open\"}");
        assertEquals("GMDEV-52", WraithTickets.key(open));
        assertFalse(WraithTickets.isClosed(open));
        assertFalse(WraithTickets.isNotFound(open));

        JsonNode closed = new ObjectMapper().readTree("{\"key\":\"GMDEV-50\",\"status\":\"closed\"}");
        assertTrue(WraithTickets.isClosed(closed));
        assertFalse(WraithTickets.isNotFound(closed));
    }

    @Test
    void aKeyJiraNoLongerHasIsNeitherOpenNorClosed() throws Exception {
        JsonNode gone = new ObjectMapper().readTree("{\"key\":\"GMDEV-404\",\"status\":\"notfound\"}");
        assertTrue(WraithTickets.isNotFound(gone));
        assertFalse(WraithTickets.isClosed(gone));
        assertEquals("GMDEV-404", WraithTickets.key(gone));
    }

    @Test
    void anUnmatchedOrEmptyLabelChoosesNothing() throws Exception {
        assertNull(chosen("{\"" + LABEL + "\":[]}"));
        assertNull(chosen("{\"alertmanager:other\":[{\"key\":\"GMDEV-52\",\"status\":\"open\"}]}"));
        assertNull(chosen("{\"" + LABEL + "\":[{\"status\":\"open\"}]}"));
    }
}
