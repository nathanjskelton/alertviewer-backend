package gmdev.platform.logviewer.data;

import gmdev.platform.logviewer.util.LogEntryStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AlertManagerRepo extends MongoRepository<AlertManagerEntry, String> {

    public List<AlertManagerEntry> findByRegexFalse();

}
