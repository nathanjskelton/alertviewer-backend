package gmdev.platform.alertviewer.ingest;

import gmdev.platform.alertviewer.data.AlertManagerEntry;
import gmdev.platform.alertviewer.util.LogEntryStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.util.AssertionErrors.fail;

public class AlertIngesterScenario1Test extends AlertIngesterAbstract {

    @Test
    public void alertIngesterScenario1Test() throws Exception {
        doIngest();
    }

    @Override
    protected int getScenario() {
        return 1;
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

    protected void runScenario() {
        alertIngester.ingest();

    }


}
