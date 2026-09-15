package net.njsdomain.alertviewer.data;

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

    //the routing tree comes from the status api. v2 is used regardless of the configured
    //api version: it carries the resolved config at config.original and is available even
    //on instances this app talks to over v1.
    public String getStatusUrl() {
        return url + "/api/v2/status";
    }

    public String getStatusUrlV1() {
        return url + "/api/v1/status";
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
