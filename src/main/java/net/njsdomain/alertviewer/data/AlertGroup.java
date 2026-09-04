package net.njsdomain.alertviewer.data;

import net.njsdomain.alertviewer.util.LogEntryStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


@Document(collection = "AlertGroup")
public class AlertGroup {
    @Id
    private String id;

    private int total;

    private int firing;

    private List<AlertManagerEntry> list;


    public AlertGroup() {
        this.total = 0;
        this.firing = 0;
        list = new ArrayList<>();
    }
    public AlertGroup(List<AlertManagerEntry> list) {
        this.total = 0;
        this.firing = 0;
        this.list = new ArrayList<>();
        for (AlertManagerEntry ame:list) {
            add(ame);
        }
    }

    public void add(AlertManagerEntry entry) {
        this.list.add(entry);
        total++;
        if (LogEntryStatus.NEW.equals(entry.getStatus()) || LogEntryStatus.FLAPPING.equals(entry.getStatus())) {
            firing++;
        }
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getFiring() {
        return firing;
    }

    public void setFiring(int firing) {
        this.firing = firing;
    }

    public List<AlertManagerEntry> getList() {
        return list;
    }
}
