package net.njsdomain.alertviewer.ingest;

import com.mongodb.client.result.UpdateResult;
import net.njsdomain.alertviewer.data.AlertManagerConfig;
import net.njsdomain.alertviewer.data.AlertManagerEntry;
import net.njsdomain.alertviewer.data.AlertManagerEntryRepo;
import net.njsdomain.alertviewer.data.AlertLabelValue;
import net.njsdomain.alertviewer.data.alert.Alert;
import net.njsdomain.alertviewer.data.jira.WraithUrls;
import net.njsdomain.alertviewer.ingest.alertmananer.AlertIngester;
import net.njsdomain.alertviewer.ingest.alertmananer.AlertManagerClient;
import net.njsdomain.alertviewer.ingest.alertmananer.AlertManagerConfigParser;
import net.njsdomain.alertviewer.ingest.alertmananer.AlertManagerUtil;
import net.njsdomain.alertviewer.server.StateBuffer;
import org.assertj.core.util.Lists;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.Mock;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
public abstract class AlertIngesterAbstract {
    private static final Logger log = LoggerFactory.getLogger(AlertIngesterAbstract.class);

    @InjectMocks
    AlertManagerUtil amUtil;

    @Mock
    Environment env;

    @Mock
    StateBuffer stateBuffer;

    @Mock
    AlertManagerClient alertManagerClient;

    //@InjectMocks leaves any @Autowired field it has no mock for null, so the
    //ingester's routing refresh would NPE. The mocked client returns no yaml, so
    //these scenarios take the early return and never reach the parser.
    @Mock
    AlertManagerConfigParser configParser;

    @Mock
    AlertManagerEntryRepo repo;

    //unstubbed, so every url comes back null: these scenarios run as a deployment with
    //no wraith behind it, where the ingester's jira work skips itself
    @Mock
    WraithUrls wraith;

    @Mock
    MongoTemplate mongo;

    @InjectMocks
    AlertIngester alertIngester;

    private final Map<String, AlertManagerEntry> databaseBefore = new HashMap<>();
    private final Map<String, AlertManagerEntry> databaseActive = new HashMap<>();

    protected abstract void mockAlertManager();

    public void bootstrapIngest() {
        mockAlertManager();

        log.info("*** Running ingester scenario "+getScenario()+": "+getDescription()+" ***");

        try {
            //env
            given(env.getProperty(eq("resolved.remove.minutes"), any(String.class))).willReturn("30");
            given(env.getProperty(eq("flapping.timeout.minutes"), any(String.class))).willReturn("15");

            //client
            given(alertManagerClient.sendRequest(any(StateBuffer.class), any(AlertManagerConfig.class), any(HttpRequest.class)))
                    .willAnswer(new Answer<JSONArray>() {
                        @Override
                        public JSONArray answer(InvocationOnMock invocationOnMock) throws Throwable {
                            try {
                                AlertManagerConfig amConfig = invocationOnMock.getArgument(1, AlertManagerConfig.class);
                                JSONArray json;
                                if ("v2".equals(amConfig.getApiVersion())) {
                                    json = new JSONArray(getAlertsResponse());
                                    log.info("API v2 configured");
                                } else {
                                    JSONObject jsonObject = new JSONObject(getAlertsResponse());
                                    json = jsonObject.getJSONArray("data");
                                    log.info("API v1 configured");
                                }
                                return json;
                            } catch(Throwable t) {
                                fail("Unable to get alerts: "+t.getMessage());
                                throw t;
                            }
                        }
                    });
            given(alertManagerClient.getSilences(any(StateBuffer.class), any()))
                    .willAnswer(new Answer<JSONArray>() {
                        @Override
                        public JSONArray answer(InvocationOnMock invocationOnMock) throws Throwable {
                            try {
                                AlertManagerConfig amConfig = invocationOnMock.getArgument(1, AlertManagerConfig.class);
                                return amUtil.getJsonArray(amConfig, getSilencesResponse());
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
                ame.setAlertmanager("TestAlertManager");
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

            //the ingest records the teams and environments it saw. Answer the upsert so
            //it does not spend the run logging a failure per value against a bare mock
            Mockito.lenient().when(mongo.upsert(any(), any(), eq(AlertLabelValue.class)))
                    .thenAnswer(invocation -> {
                        UpdateResult result = Mockito.mock(UpdateResult.class);
                        Mockito.lenient().when(result.getUpsertedId()).thenReturn(null);
                        return result;
                    });

            //the expiry reads the resolved alerts that have aged out before deciding
            //what to delete. None of these scenarios are about expiry, and the ones
            //that flip an alert between firing and resolved leave it with an end time
            //old enough to be swept up, so report nothing expired rather than delete
            //alerts the steps still assert on. Nothing is found, so nothing is removed
            given(mongo.find(any(), eq(AlertManagerEntry.class))).willReturn(Lists.newArrayList());

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
            newEntry.setAlertmanager("TestAlertManager");
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
