package gmdev.platform.alertviewer.ingest;

import gmdev.platform.alertviewer.data.AlertManagerEntry;
import gmdev.platform.alertviewer.util.LogEntryStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.util.AssertionErrors.fail;

public class AlertIngesterScenario2Test extends AlertIngesterAbstract {

    @Test
    public void alertIngesterScenario2Test() throws Exception {
        doIngest();
    }

    @Override
    protected int getScenario() {
        return 2;
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
    protected void runScenario() {
        alertIngester.ingest();

        assertTrue(getBefore().size() == 4);
        assertTrue(getAfter().size() == 4);

        assertTrue(getBefore().get("ALERT3").getStatus().equals(LogEntryStatus.NEW), "ALERT3 before was "+getBefore().get("ALERT3").getStatus()+", expected NEW");
        assertTrue(getAfter().get("ALERT3").getStatus().equals(LogEntryStatus.RESOLVED), "ALERT3 after was "+getAfter().get("ALERT3").getStatus()+", expected RESOLVED");

        assertTrue(getBefore().get("ALERT4").getStatus().equals(LogEntryStatus.NEW), "ALERT4 before was "+getBefore().get("ALERT4").getStatus()+", expected NEW");
        assertTrue(getAfter().get("ALERT4").getStatus().equals(LogEntryStatus.RESOLVED), "ALERT4 after was "+getAfter().get("ALERT4").getStatus()+", expected RESOLVED");
    }

}
