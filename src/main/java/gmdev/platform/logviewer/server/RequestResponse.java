package gmdev.platform.logviewer.server;

import gmdev.platform.logviewer.data.AlertManagerEntry;

import java.util.List;

public class RequestResponse {

    private boolean export;
    private List<AlertManagerEntry> entries;
    private StringBuilder content;

    private List<String> instances;

    private List<String> severities;

    public RequestResponse(List<AlertManagerEntry> list, List<String> instances,
                           List<String> severities, boolean export) {
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
        content.append(entry.getAlert().getLabels().get("alertname"));
        content.append("\n");
        content.append(entry.getAlert().getAnnotations().get("summary"));
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
}
