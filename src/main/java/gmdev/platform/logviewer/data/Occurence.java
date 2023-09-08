package gmdev.platform.logviewer.data;

import org.springframework.data.annotation.Transient;

import java.time.LocalDateTime;

public class Occurence implements Comparable<Occurence> {

    private LocalDateTime time;
    private String id;

    @Transient private String url;

    public Occurence(LocalDateTime time, String id) {
        this.time = time;
        this.id = id;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    //reverse sort
    @Override
    public int compareTo(Occurence o) {
        if (o != null) {
            if (time.isEqual(o.time)) {
                return 0;
            } else if (time.isAfter(o.getTime())) {
                return -1;
            }
        }
        return 1;
    }
}
