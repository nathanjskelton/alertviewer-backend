package gmdev.platform.alertviewer.data;

public class AlertManagerConfig {

    private int instance;

    private String name;

    private String url;

    private String apiVersion;

    public AlertManagerConfig(int instance, String name, String url, String apiVersion) {
        this.instance = instance;
        this.name = name;
        this.url = url;
        this.apiVersion = apiVersion;
    }

    public int getInstance() {
        return instance;
    }

    public String getName() {
        return name;
    }

    public String getApiVersion() { return apiVersion; }

    public String getAlertsUrl() {
        return url + "/api/" + apiVersion + "/alerts";
    }

    public String getSilencesUrl() {
        return url + "/api/" + apiVersion + "/silences";
    }

    public String getSilenceUrl() {
        return url + "/api/" + apiVersion + "/silence";
    }

    @Override
    public String toString() {
        return "AlertManagerConfig{" +
                "instance=" + instance +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", apiVersion='" + apiVersion + '\'' +
                '}';
    }
}
