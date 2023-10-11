package gmdev.platform.alertviewer.server;

public class CombineSuggestion {

    private String suggestion;
    private String existingId;

    public CombineSuggestion(String suggestion, String existingId) {
        this.suggestion = suggestion;
        this.existingId = existingId;
    }

    public CombineSuggestion(String suggestion) {
        this.suggestion = suggestion;
        this.existingId = null;

    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getExistingId() {
        return existingId;
    }

    public void setExistingId(String existingId) {
        this.existingId = existingId;
    }
}
