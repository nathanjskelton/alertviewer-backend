package gmdev.platform.alertviewer.server;

import gmdev.platform.alertviewer.data.AlertManagerUser;

import java.util.Collection;

public class UsersResponse {

    private Collection<AlertManagerUser> users;


    public UsersResponse(Collection<AlertManagerUser> users) {
        this.users = users;
    }

    public Collection<AlertManagerUser> getUsers() {
        return users;
    }

}
