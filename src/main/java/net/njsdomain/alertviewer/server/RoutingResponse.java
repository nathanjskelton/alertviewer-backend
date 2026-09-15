package net.njsdomain.alertviewer.server;

import net.njsdomain.alertviewer.data.routing.AlertManagerRouting;

import java.util.List;

public class RoutingResponse {

    private List<AlertManagerRouting> alertmanagers;

    public RoutingResponse(List<AlertManagerRouting> alertmanagers) {
        this.alertmanagers = alertmanagers;
    }

    public List<AlertManagerRouting> getAlertmanagers() {
        return alertmanagers;
    }
}
