package gmdev.platform.logviewer.ingest;

import gmdev.platform.logviewer.data.MetaDataHelper;
import gmdev.platform.logviewer.server.Initializer;
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

    @Scheduled(initialDelay = 5000, fixedRateString = "${ingest.fixedrate}")
    private void ingest() {
        log.debug("Running scheduled ingest");
        log.trace("*** LAST END TIME: "+ meta.getLastEnd());
        ingester.ingest();
    }
}
