package gmdev.platform.alertviewer.data;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AlertManagerUserRepo extends MongoRepository<AlertManagerUser, String> {

    public AlertManagerUser findByDn(String dn);

}
