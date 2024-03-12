package gmdev.platform.alertviewer.data.alert;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AlertManagerEntryRepo extends MongoRepository<AlertManagerEntry, String> {

    public Optional<AlertManagerEntry> findByIdAndAlertmanager(String id, String alertmanager);

}
