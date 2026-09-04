package net.njsdomain.alertviewer.server;

import net.njsdomain.alertviewer.data.AlertManagerUser;

import java.util.Objects;

public class Registration {

    private AlertManagerUser user;

    private String token;

    private long timestamp;

    public Registration(AlertManagerUser user, String token) {
        this.user = user;
        this.token = token;
        this.timestamp = System.currentTimeMillis();
    }

    public void update() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getUser() {
        return user.getId();
    }

    public String getRole() {
        return user.getRole();
    }

    public String getToken() {
        return token;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Registration that = (Registration) o;
        return Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token);
    }

}
