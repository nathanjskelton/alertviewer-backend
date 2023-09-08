package gmdev.platform.logviewer.ingest.elastic;

import gmdev.platform.logviewer.data.MetaDataHelper;
import gmdev.platform.logviewer.ingest.EntryProcessor;
import gmdev.platform.logviewer.ingest.IngestedEntry;
import gmdev.platform.logviewer.ingest.Ingester;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileReader;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Component
@ConditionalOnProperty(value = "ingester.type", havingValue = "elastic")
public class ElasticIngester implements Ingester {

    private static final Logger log = LoggerFactory.getLogger(ElasticIngester.class);

    @Autowired
    Environment env;

    @Autowired
    MetaDataHelper meta;

    @Autowired
    EntryProcessor processor;

    @Autowired
    Parser parser;

    @Override
    public void ingest() {
        log.debug("Elastic Ingester running");

        HttpClient http = new DefaultHttpClient();

        String user = env.getProperty("elastic.user");
        String password = env.getProperty("elastic.password");
        if (user != null && !user.isEmpty()) {
            CredentialsProvider provider = new BasicCredentialsProvider();
            UsernamePasswordCredentials creds = new UsernamePasswordCredentials(user, password);
            provider.setCredentials(AuthScope.ANY, creds);
            try {
                http = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();
            } catch(Exception e) {
                log.error("Unable to create http client with credentials");
            }
        }

        HttpPost post = new HttpPost(env.getProperty("elastic.url"));
        post.addHeader("Content-Type", "application/json");

        boolean run = true;

        File file = new File("query.json");
        StringBuilder query = new StringBuilder();
        try (FileReader fr = new FileReader(file)) {
            while (fr.ready()) {
                query.append((char) fr.read());
            }
        } catch(Exception e) {
            log.error("Unable to read query.json file",e);
            run = false;
        }

        while (run) {
            try {
                LocalDateTime nowUTC = LocalDateTime.now(ZoneOffset.UTC);

                LocalDateTime lastEnd = meta.getLastEnd().plusNanos(1000000);
                LocalDateTime lastTimestamp = null;
                LocalDateTime oneDay = meta.getLastEnd().plusDays(1);
                Period period = Period.between(oneDay.toLocalDate(), nowUTC.toLocalDate());
                if (period.getDays() > 1) {
                    nowUTC = oneDay;
                    run = true;
                    log.info("It has been " + period.getDays() + " days since the last ingest, limiting to a day: " + lastEnd.toString() + " -> " + nowUTC.toString());
                } else {
                    run = false;
                    log.info("Query elastic for range: " + lastEnd.toString() + " -> " + nowUTC.toString());
                }


                String queryWithTimes = query.toString();
                queryWithTimes = queryWithTimes.replace("__start-time__", lastEnd.toString());
                queryWithTimes = queryWithTimes.replace("__end-time__", nowUTC.toString());


                log.trace("Query elastic with:\n" + queryWithTimes);
                StringEntity body = new StringEntity(queryWithTimes);
                post.setEntity(body);


                HttpResponse response = http.execute(post);
                log.debug("Query response: " + response.getStatusLine());
                List<IngestedEntry> entries = parser.parse(response.getEntity().getContent(), env.getProperty("elastic.timeformat"));
                if (entries != null && entries.size() > 0) {
                    log.info("Elastic ingester found " + entries.size() + " entries to ingest");
                    String startDelim = env.getProperty("elastic.entry.startDelim");
                    int startDelimCount = env.getProperty("elastic.entry.startDelimCount", Integer.class);
                    String endDelim = env.getProperty("elastic.entry.endDelim");
                    for (IngestedEntry ie : entries) {
                        try {
                            String msg = ie.getMessage();
                            int si = -1;
                            for (int i = 0; i < startDelimCount; ) {
                                si = msg.indexOf(startDelim, si + 1);
                                i++;
                            }
                            int ei = msg.indexOf(endDelim, si);
                            if (ei > si + 1) {
                                ie.setMessage(msg.substring(si + 1, ei));
                            } else {
                                ie.setMessage(msg.substring(si + 1));
                            }
                        } catch (Exception e) {
                            log.warn("Unable to shorten message for " + ie.getEntryId(), e);
                        }

                        if (lastTimestamp == null || ie.getTimestamp().isAfter(lastTimestamp)) {
                            lastTimestamp = ie.getTimestamp();
                        }

                        //convert time to localtime and process
                        LocalDateTime utcTime = ie.getTimestamp();
                        IngestedEntry convertedIe = new IngestedEntry(utcTime.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of("America/New_York")).toLocalDateTime(), ie.getLogType(), ie.getEntryId(), ie.getMessage());
                        processor.processIngestedEntry(convertedIe);
                    }
                }
                if (response.getStatusLine().getStatusCode() == 200) {
                    if (run) {
                        meta.setLastEnd(oneDay);
                        log.debug("incrementing timestamp to start from to +(one day) timestamp");
                    } else if (lastTimestamp != null) {
                        meta.setLastEnd(lastTimestamp);
                        log.debug("incrementing timestamp to start from to last entry timestamp");
                    } else {
                        log.debug("Not incrementing timestamp to start from");
                    }
                } else {
                    log.error("Error querying elastic: "+response.getStatusLine());
                }
            } catch (Exception e) {
                e.printStackTrace(System.out);
                run = false;
            }
        }
        log.debug("Elastic ingest complete");
    }
}
