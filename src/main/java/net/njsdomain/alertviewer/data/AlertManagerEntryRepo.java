package net.njsdomain.alertviewer.data;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AlertManagerEntryRepo extends MongoRepository<AlertManagerEntry, String> {

    public List<AlertManagerEntry> findByRegexFalse();

    public Optional<AlertManagerEntry> findByIdAndAlertmanager(String id, String alertmanager);

}