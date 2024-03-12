package gmdev.platform.alertviewer.data.alert;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.Set;

public interface AlertRepo extends MongoRepository<Alert, String> {

    public Optional<Set<Alert>> findByFingerprintAndAlertmanager(String fingerprint, String alertmanager);

    public Optional<Alert> findByFingerprintAndAnnotationHash(String fingerprint, String annotationHash);

}
