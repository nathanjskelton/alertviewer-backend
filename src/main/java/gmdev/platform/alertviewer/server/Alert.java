package gmdev.platform.alertviewer.server;

public class Alert {

    private String type;
    private String message;

    public Alert(String type, String message) {
        this.type = type;
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }
}
