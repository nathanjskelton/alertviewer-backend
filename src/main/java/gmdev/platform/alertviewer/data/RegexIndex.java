package gmdev.platform.alertviewer.data;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "RegexIndex")
public class RegexIndex {

    @Id
    private String id;

    private String logEntryId;
    private String regex;
    @Indexed private Date lastUsed;

    public RegexIndex(String logEntryId, String regex) {
        this.logEntryId = logEntryId;
        this.regex = regex;
    }

    public String getId() {
        return id;
    }

    public String getLogEntryId() {
        return logEntryId;
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public Date getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(Date lastUsed) {
        this.lastUsed = lastUsed;
    }
}
