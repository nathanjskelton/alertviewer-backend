package net.njsdomain.alertviewer.server;

import net.njsdomain.alertviewer.data.AlertGroup;
import net.njsdomain.alertviewer.data.AlertManagerEntry;
import net.njsdomain.alertviewer.data.silence.Silence;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RequestResponse {

    private boolean export;
    private Map<String, AlertGroup> entries;

    private List<String> alertmanagers;

    private Collection<Silence> silences;

    private StringBuilder content;

    private List<String> instances;

    private List<String> severities;

    private Set<String> allFields;

    public RequestResponse(Map<String, AlertGroup> map, Collection<Silence> silences, List<String> instances,
                           List<String> severities, List<String> alertmanagers, Set<String> allFields, boolean export) {
        this.export = export;
        content = new StringBuilder();

        //TODO export
        /*
        if (export) {
            for (AlertManagerEntry entry:list) {
                addEntry(entry);
            }
        } else {
            entries =list;
        }
        */

        this.entries = map;
        this.instances = instances;
        this.severities = severities;
        this.silences = silences;
        this.alertmanagers = alertmanagers;
        this.allFields = allFields;
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

    public Map<String, AlertGroup> getEntries() {
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

    public Set<String> getAllFields() {
        return allFields;
    }
}
