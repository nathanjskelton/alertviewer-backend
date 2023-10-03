package gmdev.platform.logviewer.data.jira;

import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "WraithGeneric")
public class WraithGeneric {
    private String id;

    private String summary;

    private String description;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
