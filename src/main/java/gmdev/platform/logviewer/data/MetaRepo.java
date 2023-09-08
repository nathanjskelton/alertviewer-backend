package gmdev.platform.logviewer.data;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MetaRepo extends MongoRepository<MetaData, String> {

    public List<MetaData> findAll();


}
