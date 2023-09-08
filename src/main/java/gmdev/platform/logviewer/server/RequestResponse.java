package gmdev.platform.logviewer.server;

import gmdev.platform.logviewer.data.AlertManagerEntry;

import java.util.List;

public class RequestResponse {

    private boolean export;
    private List<AlertManagerEntry> entries;
    private StringBuilder content;

    public RequestResponse(List<AlertManagerEntry> list, boolean export) {
        this.export = export;
        if (export) {
            content = new StringBuilder();
            for (AlertManagerEntry entry:list) {
                addEntry(entry);
            }
        } else {
            entries =list;
        }
    }

    private void addEntry(AlertManagerEntry entry) {
        content.append("\n# ");
        content.append(entry.getId());
        content.append("\nRange: ");
        //content.append(entry.getFirstOccurence());
        content.append(" - ");
        //content.append(entry.getLastOccurence());
        content.append("\n");
        content.append("```");
        content.append("\n");
        //content.append(entry.getMessage());
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
}
