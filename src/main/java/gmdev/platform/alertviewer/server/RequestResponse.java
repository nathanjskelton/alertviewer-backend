package gmdev.platform.alertviewer.server;

import gmdev.platform.alertviewer.data.alert.AlertManagerEntry;
import gmdev.platform.alertviewer.data.silence.Silence;

import java.util.Collection;
import java.util.List;

public class RequestResponse {

    private boolean export;
    private List<AlertManagerEntry> entries;

    private List<String> alertmanagers;

    private Collection<Silence> silences;

    private StringBuilder content;

    private List<String> instances;

    private List<String> severities;

    public RequestResponse(List<AlertManagerEntry> list, Collection<Silence> silences, List<String> instances,
                           List<String> severities, List<String> alertmanagers, boolean export) {
        this.export = export;
        content = new StringBuilder();
        if (export) {
            for (AlertManagerEntry entry:list) {
                addEntry(entry);
            }
        } else {
            entries =list;
        }
        this.instances = instances;
        this.severities = severities;
        this.silences = silences;
        this.alertmanagers = alertmanagers;
    }

    private void addEntry(AlertManagerEntry entry) {
        content.append("\n# ");
        content.append(entry.getId());
        content.append("\nRange: ");
        content.append(entry.getFriendlyStartTime());
        content.append(" - ");
        content.append(entry.getFriendlyEndTime());
        content.append("\n");
        content.append("```");
        content.append("\n");
        //content.append(entry.getAlert().getLabels().get("alertname"));
        content.append("\n");
        //content.append(entry.getAlert().getAnnotations().get("summary"));
        content.append("\n");
        content.append("```");
        content.append("\n");
    }

    public List<AlertManagerEntry> getEntries() {
        return entries;
    }

    public String getContent() {
        return content.toString();
    }

    public boolean isExport() {
        return export;
    }

    public List<String> getInstances() {
        return instances;
    }

    public List<String> getSeverities() {
        return severities;
    }

    public Collection<Silence> getSilences() {
        return silences;
    }

    public List<String> getAlertmanagers() {
        return alertmanagers;
    }
}
