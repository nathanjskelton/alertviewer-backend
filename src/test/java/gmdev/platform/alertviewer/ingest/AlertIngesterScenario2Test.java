package gmdev.platform.alertviewer.ingest;

import gmdev.platform.alertviewer.data.AlertManagerEntry;
import gmdev.platform.alertviewer.util.LogEntryStatus;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.util.AssertionErrors.fail;

public class AlertIngesterScenario2Test extends AlertIngesterAbstract {
    private static final Logger log = LoggerFactory.getLogger(AlertIngesterScenario2Test.class);

    private int step = 1;

    @Test
    public void alertIngesterScenario2Test() throws Exception {
        bootstrapIngest();
    }

    @Override
    protected int getScenario() {
        return 2;
    }

    @Override
    protected String getDescription() {
        return "Flapping Test";
    }

    @Override
    protected AlertManagerEntry setupScenarioEntry(AlertManagerEntry ame) {
        switch (ame.getId()) {
            case "ALERT1":
                ame.setFlapping(false);
                ame.setAcked(false);
                ame.setStatus(LogEntryStatus.NEW);
                ame.setLastChange(System.currentTimeMillis() - (1000*300)); //5 mins ago
                break;
            case "ALERT2":
                ame.setFlapping(false);
                ame.setAcked(false);
                ame.setStatus(LogEntryStatus.NEW);
                ame.setLastChange(System.currentTimeMillis() - (1000*300)); //5 mins ago
                break;

            case "ALERT3":
                ame.setFlapping(false);
                ame.setAcked(false);
                ame.setStatus(LogEntryStatus.NEW);
                ame.setLastChange(System.currentTimeMillis() - (1000*300)); //5 mins ago
                break;

            case "ALERT4":
                ame.setFlapping(false);
                ame.setAcked(false);
                ame.setStatus(LogEntryStatus.NEW);
                ame.setLastChange(System.currentTimeMillis() - (1000*300)); //5 mins ago
                break;

            default:
                fail("Alert ID "+ame.getId()+" is not one of the IDs expected by the test");
        }
        return ame;
    }

    @Override
    protected String getAlertsResponse() throws IOException {
        log.info("Read Alerts file "+step);
        return Files.readString(new File("src/test/resources/scenario"+getScenario()+"/alerts"+step+".json").toPath());

    }

    @Override
    protected String getSilencesResponse() throws IOException {
        log.info("Read Silences file "+step);
        return Files.readString(new File("src/test/resources/scenario"+getScenario()+"/silences"+step+".json").toPath());
    }

    @Override
    protected void runScenario() {
        step = 1; //alert 4 ON
        alertIngester.ingest();

        assertTrue(getDatabaseBefore().size() == 4);
        assertTrue(getDatabaseActive().size() == 4);
        assertFalse(getDatabaseActive().get("ALERT4").isFlapping(), "STEP"+step+": ALERT4 IS flapping and should NOT be flapping");
        assertTrue(getDatabaseActive().get("ALERT4").getStatus().equals(LogEntryStatus.NEW), "STEP"+step+": ALERT4 should be NEW, is "+getDatabaseActive().get("ALERT4").getStatus());

        snapshotDatabase();
        step = 2; //alert 4 OFF
        alertIngester.ingest();
        assertFalse(getDatabaseActive().get("ALERT4").isFlapping(), "STEP"+step+": ALERT4 IS flapping and should NOT be flapping");
        assertTrue(getDatabaseActive().get("ALERT4").getStatus().equals(LogEntryStatus.RESOLVED), "STEP"+step+": ALERT4 should be RESOLVED, is "+getDatabaseActive().get("ALERT4").getStatus());

        snapshotDatabase();
        step = 3; //alert 4 ON after <15m so should be flapping
        alertIngester.ingest();
        assertTrue(getDatabaseActive().get("ALERT4").isFlapping(), "STEP"+step+": ALERT4 IS NOT flapping and SHOULD be flapping");
        assertTrue(getDatabaseActive().get("ALERT4").getStatus().equals(LogEntryStatus.NEW), "STEP"+step+": ALERT4 should be NEW, is "+getDatabaseActive().get("ALERT4").getStatus());

        snapshotDatabase();
        step = 4; //alert 4 OFF
        alertIngester.ingest();
        assertTrue(getDatabaseActive().get("ALERT4").isFlapping(), "STEP"+step+": ALERT4 IS NOT flapping and SHOULD be flapping");
        assertTrue(getDatabaseActive().get("ALERT4").getStatus().equals(LogEntryStatus.NEW), "STEP"+step+": ALERT4 should be NEW, is "+getDatabaseActive().get("ALERT4").getStatus());

        snapshotDatabase();
        step = 4; //alert 4 OFF
        getDatabaseActive().get("ALERT4").setLastChange(System.currentTimeMillis() - (60000 * 16));
        alertIngester.ingest();
        assertFalse(getDatabaseActive().get("ALERT4").isFlapping(), "STEP"+step+"+16s: ALERT4 IS flapping and should NOT be flapping");
        assertTrue(getDatabaseActive().get("ALERT4").getStatus().equals(LogEntryStatus.RESOLVED), "STEP"+step+"+16s: ALERT4 should be RESOLVED, is "+getDatabaseActive().get("ALERT4").getStatus());

        snapshotDatabase();
        step = 5; //alert 4 ON, after 15 minutes so should be NEW, not flapping
        getDatabaseActive().get("ALERT4").setLastChange(System.currentTimeMillis() - (60000 * 16));
        alertIngester.ingest();
        assertFalse(getDatabaseActive().get("ALERT4").isFlapping(), "STEP"+step+": ALERT4 IS flapping and should NOT be flapping");
        assertTrue(getDatabaseActive().get("ALERT4").getStatus().equals(LogEntryStatus.NEW), "STEP"+step+": ALERT4 should be NEW, is "+getDatabaseActive().get("ALERT4").getStatus());

        snapshotDatabase();
        step = 6; //alert 4 OFF
        alertIngester.ingest();
        assertFalse(getDatabaseActive().get("ALERT4").isFlapping(), "ALERT4 IS flapping and should NOT be flapping");
        assertTrue(getDatabaseActive().get("ALERT4").getStatus().equals(LogEntryStatus.RESOLVED), "STEP"+step+": ALERT4 should be RESOLVED, is "+getDatabaseActive().get("ALERT4").getStatus());

        snapshotDatabase();
        step = 7; //alert 4 ON
        alertIngester.ingest();
        assertTrue(getDatabaseActive().get("ALERT4").isFlapping() == true, "ALERT4 IS NOT flapping and SHOULD be flapping");

        snapshotDatabase();
        step = 7; //alert 4 ON again after 15s, flapping should go off
        getDatabaseActive().get("ALERT4").setLastChange(System.currentTimeMillis() - (60000 * 16));
        alertIngester.ingest();
        assertFalse(getDatabaseActive().get("ALERT4").isFlapping() == true, "ALERT4 IS flapping and should NOT be flapping");

    }

}
