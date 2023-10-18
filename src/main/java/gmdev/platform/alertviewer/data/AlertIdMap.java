package gmdev.platform.alertviewer.data;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "AlertIdMap")
public class AlertIdMap {

    @Id
    private String id;

    private String alertmanager;

    public AlertIdMap(String id, String alertmanager) {
        this.alertmanager = alertmanager;
        this.id = id;
    }

    public String getAlertmanager() {
        return alertmanager;
    }

    public String getId() {
        return id;
    }

}
