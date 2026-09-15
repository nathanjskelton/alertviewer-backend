package net.njsdomain.alertviewer.ingest.alertmananer;

import net.njsdomain.alertviewer.data.routing.AlertManagerRouting;
import net.njsdomain.alertviewer.data.routing.ReceiverAction;
import net.njsdomain.alertviewer.data.routing.ReceiverInfo;
import net.njsdomain.alertviewer.data.routing.RouteNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertManagerConfigParserTest {

    //covers what alertmanager really emits: modern matchers, both legacy forms,
    //continue, a child overriding one timing while inheriting the rest, a receiver
    //with two integrations, one with none, and a redacted secret
    private static final String YAML =
            "global:\n" +
            "  resolve_timeout: 5m\n" +
            "route:\n" +
            "  receiver: default-receiver\n" +
            "  group_by:\n" +
            "  - system\n" +
            "  - group\n" +
            "  continue: false\n" +
            "  group_wait: 1m\n" +
            "  group_interval: 5m\n" +
            "  repeat_interval: 1m\n" +
            "  routes:\n" +
            "  - receiver: wraith\n" +
            "    matchers:\n" +
            "    - jira=\"true\"\n" +
            "    - severity=~\"crit|warn\"\n" +
            "    - team!=\"noc\"\n" +
            "    continue: true\n" +
            "    group_wait: 10s\n" +
            "  - receiver: pager\n" +
            "    match:\n" +
            "      severity: critical\n" +
            "    match_re:\n" +
            "      instance: app-.*\n" +
            "    mute_time_intervals:\n" +
            "    - out-of-hours\n" +
            "  - receiver: bugle\n" +
            "    continue: false\n" +
            "receivers:\n" +
            "- name: default-receiver\n" +
            "- name: bugle\n" +
            "  webhook_configs:\n" +
            "  - send_resolved: true\n" +
            "    http_config:\n" +
            "      follow_redirects: true\n" +
            "    url: http://bugle:4567/v1/alerts\n" +
            "    max_alerts: 0\n" +
            "- name: pager\n" +
            "  pagerduty_configs:\n" +
            "  - routing_key: <secret>\n" +
            "    send_resolved: false\n" +
            "  slack_configs:\n" +
            "  - channel: '#ops'\n" +
            "    api_url: <secret>\n" +
            "- name: unused-receiver\n" +
            "  email_configs:\n" +
            "  - to: ops@example.com\n" +
            "time_intervals:\n" +
            "- name: out-of-hours\n";

    private AlertManagerConfigParser parser;
    private AlertManagerRouting routing;

    @BeforeEach
    void setUp() throws Exception {
        parser = new AlertManagerConfigParser();
        routing = parser.parse("alertmanager1", YAML);
    }

    @Test
    void readsTheRootRoute() {
        RouteNode root = routing.getRoute();
        assertEquals("0", root.getId());
        assertEquals("default-receiver", root.getReceiver());
        assertEquals(List.of("system", "group"), root.getGroupBy());
        assertEquals("1m", root.getGroupWait());
        assertTrue(root.getMatchers().isEmpty(), "root route matches everything");
        assertEquals(3, root.getRoutes().size());
    }

    @Test
    void splitsAllThreeMatcherOperators() {
        RouteNode wraith = routing.getRoute().getRoutes().get(0);
        assertEquals(3, wraith.getMatchers().size());
        assertEquals("jira", wraith.getMatchers().get(0).getName());
        assertEquals("=", wraith.getMatchers().get(0).getOp());
        assertEquals("true", wraith.getMatchers().get(0).getValue());
        assertEquals("=~", wraith.getMatchers().get(1).getOp());
        assertEquals("crit|warn", wraith.getMatchers().get(1).getValue());
        assertEquals("!=", wraith.getMatchers().get(2).getOp());
        assertTrue(wraith.isContinueOnMatch());
    }

    @Test
    void readsLegacyMatchAndMatchRe() {
        RouteNode pager = routing.getRoute().getRoutes().get(1);
        assertEquals(2, pager.getMatchers().size());
        assertTrue(pager.getMatchers().stream()
                .anyMatch(m -> "severity".equals(m.getName()) && "=".equals(m.getOp()) && "critical".equals(m.getValue())));
        assertTrue(pager.getMatchers().stream()
                .anyMatch(m -> "instance".equals(m.getName()) && "=~".equals(m.getOp()) && "app-.*".equals(m.getValue())));
        assertEquals(List.of("out-of-hours"), pager.getMuteTimeIntervals());
    }

    @Test
    void resolvesInheritanceAndSaysWhatWasInherited() {
        RouteNode wraith = routing.getRoute().getRoutes().get(0);
        //set on the child
        assertEquals("10s", wraith.getGroupWait());
        assertFalse(wraith.getInherited().get("groupWait"));
        assertFalse(wraith.getInherited().get("receiver"));
        //cascaded from the root
        assertEquals("5m", wraith.getGroupInterval());
        assertTrue(wraith.getInherited().get("groupInterval"));
        assertEquals(List.of("system", "group"), wraith.getGroupBy());
        assertTrue(wraith.getInherited().get("groupBy"));
    }

    @Test
    void flattensEveryIntegrationIntoActions() {
        ReceiverInfo bugle = receiver("bugle");
        assertEquals(1, bugle.getActions().size());
        ReceiverAction webhook = bugle.getActions().get(0);
        assertEquals("webhook", webhook.getType());
        assertEquals("http://bugle:4567/v1/alerts", webhook.getTarget());
        assertEquals(Boolean.TRUE, webhook.getSendResolved());
        //nested blocks are not destinations and must not leak into the detail line
        assertFalse(webhook.getDetails().containsKey("http_config"));
        assertEquals("0", webhook.getDetails().get("max_alerts"));

        ReceiverInfo pager = receiver("pager");
        assertEquals(2, pager.getActions().size(), "two integrations on one receiver");
    }

    @Test
    void prefersTheReadableTargetOverTheSecretOne() {
        //slack: channel is what an operator recognises, api_url is the secret
        ReceiverAction slack = receiver("pager").getActions().stream()
                .filter(a -> "slack".equals(a.getType())).findFirst().orElseThrow();
        assertEquals("#ops", slack.getTarget());

        //pagerduty has only the redacted key, so that is what shows
        ReceiverAction pd = receiver("pager").getActions().stream()
                .filter(a -> "pagerduty".equals(a.getType())).findFirst().orElseThrow();
        assertEquals("<secret>", pd.getTarget());
        assertEquals(Boolean.FALSE, pd.getSendResolved());
    }

    @Test
    void aReceiverWithNoIntegrationsHasNoActions() {
        //meaningful, not missing: anything routed here is silently discarded
        assertTrue(receiver("default-receiver").getActions().isEmpty());
        assertTrue(receiver("default-receiver").isUsed());
    }

    @Test
    void marksReceiversNoRouteReferences() {
        assertFalse(receiver("unused-receiver").isUsed());
    }

    @Test
    void doesNotClaimInheritanceFromAParentThatSetNothing() throws Exception {
        //a config with no group_by anywhere must not report the children as having
        //inherited one -- there is nothing to inherit
        AlertManagerRouting r = parser.parse("am2",
                "route:\n" +
                "  receiver: default-receiver\n" +
                "  group_wait: 10s\n" +
                "  routes:\n" +
                "  - receiver: bugle\n" +
                "receivers:\n" +
                "- name: default-receiver\n" +
                "- name: bugle\n");
        RouteNode child = r.getRoute().getRoutes().get(0);
        assertTrue(child.getGroupBy().isEmpty());
        assertFalse(child.getInherited().get("groupBy"));
        //the timing genuinely is inherited, so that one stays true
        assertEquals("10s", child.getGroupWait());
        assertTrue(child.getInherited().get("groupWait"));
    }

    @Test
    void collectsTimeIntervalNames() {
        assertEquals(List.of("out-of-hours"), routing.getTimeIntervals());
    }

    @Test
    void keepsAlertmanagersOwnTextForAnUnsplittableMatcher() {
        var m = AlertManagerConfigParser.parseMatcher("{weird = thing}");
        assertNull(m.getName());
        assertEquals("{weird = thing}", m.getRaw());
    }

    private ReceiverInfo receiver(String name) {
        return routing.getReceivers().stream()
                .filter(r -> name.equals(r.getName())).findFirst().orElse(null);
    }
}
