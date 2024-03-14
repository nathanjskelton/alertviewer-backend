package gmdev.platform.alertviewer.data.jira;

import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "WraithGeneric")
public class WraithGeneric {
    private String id;

    private String summary;

    private String description;

    private String environment;

    private String system;

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

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    @Override
    public String toString() {
        return "WraithGeneric{" +
                "id='" + id + '\'' +
                ", summary='" + summary + '\'' +
                ", description='" + description + '\'' +
                ", environment='" + environment + '\'' +
                ", system='" + system + '\'' +
                '}';
    }
}
