package net.njsdomain.alertviewer.ingest;

import net.njsdomain.alertviewer.data.MetaDataHelper;
import net.njsdomain.alertviewer.server.Initializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IngestScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestScheduler.class);

    @Autowired
    Initializer initializer;

    @Autowired
    MetaDataHelper meta;

    @Autowired
    Ingester ingester;

    public static final long rate = 30000L;

    @Scheduled(initialDelay = 5000, fixedRateString = rate+"")
    private void ingest() {
        log.debug("Running scheduled ingest");
        log.trace("*** LAST END TIME: "+ meta.getLastEnd());
        ingester.ingest();
    }
}
