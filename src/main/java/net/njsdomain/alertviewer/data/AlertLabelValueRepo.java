package net.njsdomain.alertviewer.data;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertLabelValueRepo extends MongoRepository<AlertLabelValue, String> {

    List<AlertLabelValue> findByAlertmanagerAndType(String alertmanager, String type);
}
