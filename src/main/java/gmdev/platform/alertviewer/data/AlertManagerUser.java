package gmdev.platform.alertviewer.data;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Objects;

@Document(collection = "AlertManagerUser")
public class AlertManagerUser {

    @Id
    private String id;

    private String dn;

    private String role;

    private boolean active = true;

    public AlertManagerUser(String id, String dn, String role, boolean active) {
        this.id = id;
        this.dn = dn;
        this.role = role;
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlertManagerUser that = (AlertManagerUser) o;
        return Objects.equals(id, that.id) && Objects.equals(dn, that.dn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, dn);
    }


}
