package gmdev.platform.alertviewer.data;

public class AlertManagerConfig {

    private int instance;

    private String name;

    private String url;

    public AlertManagerConfig(int instance, String name, String url) {
        this.instance = instance;
        this.name = name;
        this.url = url;
    }

    public int getInstance() {
        return instance;
    }

    public String getName() {
        return name;
    }

    public String getAlertsUrl() {
        return url + "/api/v1/alerts";
    }

    public String getSilencesUrl() {
        return url + "/api/v1/silences";
    }

    public String getSilenceUrl() {
        return url + "/api/v1/silence";
    }

}
