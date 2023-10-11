package gmdev.platform.alertviewer.data;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AlertManagerRepo extends MongoRepository<AlertManagerEntry, String> {

    public List<AlertManagerEntry> findByRegexFalse();

}
