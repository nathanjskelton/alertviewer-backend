package gmdev.platform.alertviewer.server;

import gmdev.platform.alertviewer.data.AlertManagerRepo;
import gmdev.platform.alertviewer.data.AlertManagerEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class MergeAllAsync {

    private static final Logger log = LoggerFactory.getLogger(MergeAllAsync.class);

    @Autowired
    AlertManagerRepo repo;

    @Autowired CombineService combine;

    @Autowired StateBuffer state;

    @Async
    public CompletableFuture<String> mergeAll() throws ServiceException {
        if (!state.aquireLock()) {
            throw new ServiceException("Lock enabled, try again later");
        }

        try {
            log.debug("Start mergeAll...");
            List<AlertManagerEntry> all = repo.findByRegexFalse();
            int count = 0;
            for (AlertManagerEntry entry : all) {
                try {
                    count = count + combine.merge(Arrays.asList(new String[]{entry.getId()}));
                } catch (ServiceException se) {
                    state.releaseLock();
                    log.debug(se.getMessage());
                }
            }

            String msg = count + " records merged into existing regex";
            log.info(msg);
            state.releaseLock();
            return CompletableFuture.completedFuture(msg);
        } finally {
            state.releaseLock();
        }
    }
}
