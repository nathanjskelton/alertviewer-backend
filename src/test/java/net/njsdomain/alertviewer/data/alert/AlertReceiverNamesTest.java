package net.njsdomain.alertviewer.data.alert;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two api versions persist receivers differently -- v2 as objects, v1 as bare
 * strings -- and both shapes are already in the database. Both must normalise.
 */
class AlertReceiverNamesTest {

    @Test
    void readsTheV2ObjectShape() {
        Map<String, String> receiver = new LinkedHashMap<>();
        receiver.put("name", "bugle");
        assertEquals(List.of("bugle"), namesOf(receiver));
    }

    @Test
    void readsTheV1StringShape() {
        assertEquals(List.of("bugle"), namesOf("bugle"));
    }

    @Test
    void handlesBothShapesTogetherAndDeduplicates() {
        Map<String, String> receiver = new LinkedHashMap<>();
        receiver.put("name", "wraith");
        assertEquals(List.of("bugle", "wraith"), namesOf("bugle", receiver, "bugle"));
    }

    @Test
    void isEmptyRatherThanNullWhenNothingWasRecorded() {
        Alert alert = new Alert();
        assertTrue(alert.getReceiverNames().isEmpty());
    }

    private List<String> namesOf(Object... receivers) {
        Alert alert = new Alert();
        Set<Object> set = new LinkedHashSet<>();
        for (Object r : receivers) {
            set.add(r);
        }
        alert.setReceivers(set);
        return alert.getReceiverNames();
    }
}
