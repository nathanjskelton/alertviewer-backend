package net.njsdomain.alertviewer.data;

import net.njsdomain.alertviewer.util.LogEntryStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Acking used to overwrite an alert's status with ACKED, which meant an acked alert
 * no longer said whether it was firing. It is a flag of its own now, so the entries
 * written the old way have to be unpicked: their real status is NEW, because the
 * ingest only ever left an alert on ACKED while the alertmanager was still listing
 * it -- one that stopped firing was moved to RESOLVED regardless of the ack.
 *
 * Left alone they would match no status filter at all and quietly vanish from the UI.
 * The query matches nothing once it has run, so a restart costs one indexed lookup.
 */
@Component
public class AckedStatusMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AckedStatusMigration.class);

    @Autowired
    MongoTemplate mongo;

    @Override
    public void run(String... args) {
        try {
            long migrated = mongo.updateMulti(
                    Query.query(Criteria.where("status").is(LogEntryStatus.ACKED)),
                    new Update().set("status", LogEntryStatus.NEW).set("acked", true),
                    AlertManagerEntry.class).getModifiedCount();
            if (migrated > 0) {
                log.info("Moved " + migrated + " alerts off the retired ACKED status onto the acked flag");
            }
        } catch (Exception e) {
            //a failure here leaves those alerts hidden, which is worth a loud log, but
            //it is no reason to refuse to start and take the whole view down with it
            log.error("Could not migrate alerts off the retired ACKED status", e);
        }
    }
}
