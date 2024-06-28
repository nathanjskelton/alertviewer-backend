package gmdev.platform.alertviewer.ingest;

import com.mongodb.client.result.DeleteResult;
import gmdev.platform.alertviewer.data.AlertManagerConfig;
import gmdev.platform.alertviewer.data.AlertManagerEntry;
import gmdev.platform.alertviewer.data.AlertManagerEntryRepo;
import gmdev.platform.alertviewer.data.alert.Alert;
import gmdev.platform.alertviewer.ingest.alertmananer.AlertIngester;
import gmdev.platform.alertviewer.ingest.alertmananer.AlertManagerClient;
import gmdev.platform.alertviewer.server.StateBuffer;
import gmdev.platform.alertviewer.util.LogEntryStatus;
import org.assertj.core.util.Lists;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.File;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
public abstract class AlertIngesterAbstract {
    private static final Logger log = LoggerFactory.getLogger(AlertIngesterAbstract.class);

    @Mock
    Environment env;

    @Mock
    StateBuffer stateBuffer;

    @Mock
    AlertManagerClient alertManagerClient;

    @Mock
    AlertManagerEntryRepo repo;

    @Mock
    MongoTemplate mongo;

    @InjectMocks
    AlertIngester alertIngester;

    private final Map<String, AlertManagerEntry> databaseBefore = new HashMap<>();
    private final Map<String, AlertManagerEntry> databaseToUse = new HashMap<>();
    private final Map<String, AlertManagerEntry> databaseAfter = new HashMap<>();

    public void doIngest() throws Exception {
        log.info("*** Running ingester scenario "+getScenario()+" ***");
        //env
        //given(env.getProperty(eq("flapping.timeout.minutes"), any(String.class))).willReturn("2");
        given(env.getProperty(eq("resolved.remove.minutes"), any(String.class))).willReturn("1");
        given(env.getProperty(eq("flapping.timeout.minutes"), any(String.class))).willReturn("1");

        //alertManagers
        Map<String, AlertManagerConfig> alertmanagers = new HashMap<>();
        AlertManagerConfig amc = new AlertManagerConfig(1, "TestAlertManager1", "http://testurl1");
        alertmanagers.put("TestAlertManager1", amc);
        given(stateBuffer.getAlertmanagers()).willReturn(alertmanagers.values());

        //client
        String alertsJson = Files.readString(new File("src/test/resources/scenario"+getScenario()+"/alerts.json").toPath());
        given(alertManagerClient.sendRequest(any(StateBuffer.class), any(AlertManagerConfig.class), any(HttpRequest.class)))
                .willReturn(new JSONObject(alertsJson));
        String silencesJson = Files.readString(new File("src/test/resources/scenario"+getScenario()+"/silences.json").toPath());
        given(alertManagerClient.getSilences(any(StateBuffer.class), any()))
                .willReturn(new JSONObject(silencesJson));

        //database
        given(repo.save(any(AlertManagerEntry.class))).willAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                AlertManagerEntry entry = invocationOnMock.getArgument(0);
                return databaseAfter.put(entry.getId(), entry);
            }
        });

        String dbJson = Files.readString(new File("src/test/resources/scenario"+getScenario()+"/databaseAlerts.json").toPath());
        JSONArray data = new JSONObject(dbJson).getJSONArray("data");
        for (int i = 0;i < data.length();i++) {
            String jsonAlert = data.getJSONObject(i).toString();

            Alert alert = alertIngester.jsonToAlert(jsonAlert);
            AlertManagerEntry ame = new AlertManagerEntry(alert);
            ame.setAlertmanager(amc.getName());
            ame = setupScenarioEntry(ame);
            databaseToUse.put(ame.getId(), ame);

            Alert alert2 = alertIngester.jsonToAlert(jsonAlert);
            AlertManagerEntry ame2 = new AlertManagerEntry(alert2);
            ame2.setAlertmanager(amc.getName());
            ame2 = setupScenarioEntry(ame2);
            databaseBefore.put(ame2.getId(), ame2);
        }

        given(repo.findAll()).willReturn(Lists.newArrayList(databaseToUse.values()));
        given(repo.findByIdAndAlertmanager(any(String.class), any(String.class))).willAnswer(new Answer<Optional<AlertManagerEntry>>() {
            @Override
            public Optional<AlertManagerEntry> answer(InvocationOnMock invocationOnMock) throws Throwable {
                Optional<AlertManagerEntry> oame = Optional.empty();
                for (AlertManagerEntry entry : databaseToUse.values()) {
                    if (entry.getId().equals(invocationOnMock.getArgument(0)) &&
                            entry.getAlertmanager().equals(invocationOnMock.getArgument(1))) {
                        oame = Optional.of(entry);
                        log.info("Found a match in database for "+entry.getId());
                        break;
                    }
                }
                if (!oame.isPresent()) log.info("No match in database for "+invocationOnMock.getArgument(0));
                return oame;
            }
        });

        //records deleted
        DeleteResult dr = Mockito.mock(DeleteResult.class);
        given(dr.getDeletedCount()).willReturn(0L);
        given(mongo.remove(any(), eq(AlertManagerEntry.class))).willReturn(dr);

        //run test
        runScenario();

    }

    protected Map<String, AlertManagerEntry> getBefore() {
        return databaseBefore;
    }

    protected Map<String, AlertManagerEntry> getAfter() {
        return databaseAfter;
    }

    protected abstract AlertManagerEntry setupScenarioEntry(AlertManagerEntry ame);

    protected abstract int getScenario();

    protected abstract void runScenario();
}
