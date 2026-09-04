package net.njsdomain.alertviewer.ingest;

import net.njsdomain.alertviewer.data.AlertManagerConfig;
import net.njsdomain.alertviewer.data.AlertManagerEntry;
import net.njsdomain.alertviewer.util.LogEntryStatus;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.util.AssertionErrors.fail;

@MockitoSettings(strictness = Strictness.LENIENT)
public class AlertIngesterScenario3Test extends AlertIngesterAbstract {
    private static final Logger log = LoggerFactory.getLogger(AlertIngesterScenario3Test.class);

    private int step = 1;


    @Test
    public void alertIngesterScenario3Test() throws Exception {
        bootstrapIngest();
    }

    @Override
    protected int getScenario() {
        return 3;
    }

    @Override
    protected String getDescription() {
        return "API v2 Test";
    }

    @Override
    protected void mockAlertManager() {
        final AlertManagerConfig amc1 = new AlertManagerConfig(1, "TestAlertManager", "http://testurl", "v2");
        Map<String, AlertManagerConfig> alertmanagers = new HashMap<>();
        alertmanagers.put("TestAlertManager", amc1);
        given(stateBuffer.getAlertmanagers()).willReturn(alertmanagers.values());
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
        step = 1;
        alertIngester.ingest();
    }

}
