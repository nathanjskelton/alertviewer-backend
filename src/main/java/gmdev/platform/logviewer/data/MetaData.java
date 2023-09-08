package gmdev.platform.logviewer.data;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "MetaData")
public class MetaData {

    @Id
    private String id;

    private LocalDateTime lastEndTime;

    public LocalDateTime getLastEndTime() {
        return lastEndTime;
    }

    public void setLastEndTime(LocalDateTime lastEndTime) {
        this.lastEndTime = lastEndTime;
    }
}
