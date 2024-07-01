package gmdev.platform.alertviewer.ingest;

import com.mongodb.client.result.DeleteResult;
import gmdev.platform.alertviewer.data.AlertManagerConfig;
import gmdev.platform.alertviewer.data.AlertManagerEntry;
import gmdev.platform.alertviewer.data.AlertManagerEntryRepo;
import gmdev.platform.alertviewer.data.alert.Alert;
import gmdev.platform.alertviewer.ingest.alertmananer.AlertIngester;
import gmdev.platform.alertviewer.ingest.alertmananer.AlertManagerClient;
import gmdev.platform.alertviewer.server.StateBuffer;
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
import java.io.IOException;
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
    private final Map<String, AlertManagerEntry> databaseActive = new HashMap<>();

    final AlertManagerConfig amc1 = new AlertManagerConfig(1, "TestAlertManager1", "http://testurl1");
    final AlertManagerConfig amc2 = new AlertManagerConfig(2, "TestAlertManager2", "http://testurl2");

    public void bootstrapIngest() {
        log.info("*** Running ingester scenario "+getScenario()+": "+getDescription()+" ***");

        try {
            //env
            given(env.getProperty(eq("resolved.remove.minutes"), any(String.class))).willReturn("30");
            given(env.getProperty(eq("flapping.timeout.minutes"), any(String.class))).willReturn("15");

            //alertManagers
            Map<String, AlertManagerConfig> alertmanagers = new HashMap<>();
            alertmanagers.put("TestAlertManager1", amc1);
            given(stateBuffer.getAlertmanagers()).willReturn(alertmanagers.values());

            //client
            given(alertManagerClient.sendRequest(any(StateBuffer.class), any(AlertManagerConfig.class), any(HttpRequest.class)))
                    .willAnswer(new Answer<JSONObject>() {
                        @Override
                        public JSONObject answer(InvocationOnMock invocationOnMock) throws Throwable {
                            try {
                                return new JSONObject(getAlertsResponse());
                            } catch(Throwable t) {
                                fail("Unable to get alerts: "+t.getMessage());
                                throw t;
                            }
                        }
                    });
            given(alertManagerClient.getSilences(any(StateBuffer.class), any()))
                    .willAnswer(new Answer<JSONObject>() {
                        @Override
                        public JSONObject answer(InvocationOnMock invocationOnMock) throws Throwable {
                            try {
                                return new JSONObject(getSilencesResponse());
                            } catch(Throwable t) {
                                fail("Unable to get silences: "+t.getMessage());
                                throw t;
                            }
                        }
                    });

            //database save answer
            given(repo.save(any(AlertManagerEntry.class))).willAnswer(new Answer<Object>() {
                @Override
                public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                    AlertManagerEntry entry = invocationOnMock.getArgument(0);
                    try {
                        return databaseActive.put(entry.getId(), entry);
                    } catch(Throwable t) {
                        fail("Unable to save: "+t.getMessage());
                        throw  t;
                    }
                }
            });

            //load the initial database from the file
            String dbJson = Files.readString(new File("src/test/resources/scenario" + getScenario() + "/databaseAlerts.json").toPath());
            JSONArray data = new JSONObject(dbJson).getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                String jsonAlert = data.getJSONObject(i).toString();

                Alert alert = alertIngester.jsonToAlert(jsonAlert);
                AlertManagerEntry ame = new AlertManagerEntry(alert);
                ame.setAlertmanager(amc1.getName());
                ame = setupScenarioEntry(ame);
                databaseActive.put(ame.getId(), ame);

            }
            snapshotDatabase();

            //setup finders
            given(repo.findAll()).willAnswer(new Answer<List<AlertManagerEntry>>() {
                @Override
                public List<AlertManagerEntry> answer(InvocationOnMock invocationOnMock) throws Throwable {
                    return Lists.newArrayList(databaseActive.values());
                }
            });
            given(repo.findByIdAndAlertmanager(any(String.class), any(String.class))).willAnswer(new Answer<Optional<AlertManagerEntry>>() {
                @Override
                public Optional<AlertManagerEntry> answer(InvocationOnMock invocationOnMock) throws Throwable {
                    Optional<AlertManagerEntry> oame = Optional.empty();
                    for (AlertManagerEntry entry : databaseActive.values()) {
                        if (entry.getId().equals(invocationOnMock.getArgument(0)) &&
                                entry.getAlertmanager().equals(invocationOnMock.getArgument(1))) {
                            oame = Optional.of(entry);
                            log.info("Found a match in database for " + entry.getId());
                            break;
                        }
                    }
                    if (!oame.isPresent()) log.info("No match in database for " + invocationOnMock.getArgument(0));
                    return oame;
                }
            });

            //records deleted
            DeleteResult dr = Mockito.mock(DeleteResult.class);
            given(dr.getDeletedCount()).willReturn(0L);
            given(mongo.remove(any(), eq(AlertManagerEntry.class))).willReturn(dr);

            //run test
            runScenario();

        } catch(Throwable e) {
            fail("Exception Thrown: "+e.getMessage());
        }
    }

    protected Map<String, AlertManagerEntry> getDatabaseBefore() {
        return databaseBefore;
    }

    protected Map<String, AlertManagerEntry> getDatabaseActive() {
        return databaseActive;
    }

    protected void snapshotDatabase() {
        log.info("Update before snapshot database");
        databaseBefore.clear();
        for(AlertManagerEntry entry: databaseActive.values()) {
            AlertManagerEntry newEntry = new AlertManagerEntry(entry.getAlert());
            newEntry.setAlertmanager(amc1.getName());
            newEntry.setFlapping(entry.isFlapping());
            newEntry.setAcked(entry.isAcked());
            newEntry.setStatus(entry.getStatus());
            newEntry.setLastChange(entry.getLastChange());
            databaseBefore.put(newEntry.getId(), newEntry);
        }

    }

    protected abstract AlertManagerEntry setupScenarioEntry(AlertManagerEntry ame);

    protected abstract int getScenario();

    protected abstract String getDescription();

    protected abstract void runScenario();

    protected abstract String getAlertsResponse() throws IOException;

    protected abstract  String getSilencesResponse() throws IOException;

}
