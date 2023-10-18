package gmdev.platform.alertviewer.data;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SilenceIdMapRepo extends MongoRepository<SilenceIdMap, String> {
    

}
