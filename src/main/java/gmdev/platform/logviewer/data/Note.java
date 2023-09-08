package gmdev.platform.logviewer.data;

import java.time.LocalDateTime;

public class Note implements Comparable<Note> {

    private LocalDateTime timestamp;
    private String user;
    private String message;

    public Note(LocalDateTime timestamp, String user, String message)  {
        this.timestamp = timestamp;
        this.user = user;
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    //reverse sort
    @Override
    public int compareTo(Note o) {
        if (o != null) {
            if (timestamp.isEqual(o.timestamp)) {
                return 0;
            } else if (timestamp.isAfter(o.getTimestamp())) {
                return -1;
            }
        }
        return 1;
    }
}
