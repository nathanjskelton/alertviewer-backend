package net.njsdomain.alertviewer.server;

import net.njsdomain.alertviewer.data.AlertManagerUser;

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
