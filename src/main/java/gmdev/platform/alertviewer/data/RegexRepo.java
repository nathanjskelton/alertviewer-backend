package gmdev.platform.alertviewer.data;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface RegexRepo extends MongoRepository<RegexIndex, String> {

    public Optional<RegexIndex> findByLogEntryId(String logEntryId);

    public List<RegexIndex> findAllByOrderByLastUsedDesc();

    public void deleteByLogEntryId(String logEntryId);

}
