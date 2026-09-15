package net.njsdomain.alertviewer.ingest.alertmananer;

import net.njsdomain.alertviewer.data.routing.AlertManagerRouting;
import net.njsdomain.alertviewer.data.routing.ReceiverAction;
import net.njsdomain.alertviewer.data.routing.ReceiverInfo;
import net.njsdomain.alertviewer.data.routing.RouteMatcher;
import net.njsdomain.alertviewer.data.routing.RouteNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the yaml alertmanager publishes at /api/v2/status into the routing tree.
 *
 * The yaml is walked as a plain map rather than bound to classes: alertmanager
 * adds fields and whole integrations between releases, and a binding would have
 * to be taught each one. Nothing here is spring-aware, so it unit tests bare.
 */
@Component
public class AlertManagerConfigParser {

    private static final Logger log = LoggerFactory.getLogger(AlertManagerConfigParser.class);

    //name, operator, then the value with its optional quotes stripped
    private static final Pattern MATCHER =
            Pattern.compile("^\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*(=~|!~|!=|=)\\s*\"?(.*?)\"?\\s*$");

    //which key names the thing an operator would recognise as the destination.
    //per integration, because a single list picks wrongly: for slack api_url is
    //the secret webhook while channel is what someone actually wants to read.
    private static final Map<String, String[]> TARGET_KEYS = new LinkedHashMap<>();
    static {
        TARGET_KEYS.put("webhook", new String[]{"url"});
        TARGET_KEYS.put("email", new String[]{"to"});
        TARGET_KEYS.put("slack", new String[]{"channel", "api_url"});
        TARGET_KEYS.put("pagerduty", new String[]{"routing_key", "service_key", "url"});
        TARGET_KEYS.put("opsgenie", new String[]{"api_url"});
        TARGET_KEYS.put("victorops", new String[]{"routing_key"});
        TARGET_KEYS.put("pushover", new String[]{"user_key"});
        TARGET_KEYS.put("telegram", new String[]{"chat_id"});
        TARGET_KEYS.put("discord", new String[]{"webhook_url"});
        TARGET_KEYS.put("msteams", new String[]{"webhook_url"});
        TARGET_KEYS.put("webex", new String[]{"room_id"});
        TARGET_KEYS.put("wechat", new String[]{"to_user", "to_party", "to_tag"});
        TARGET_KEYS.put("sns", new String[]{"topic_arn", "phone_number"});
    }
    private static final String[] TARGET_FALLBACK =
            {"url", "api_url", "webhook_url", "to", "channel", "routing_key", "api_key"};

    //alertmanager redacts its own secrets as <secret>; this only guards against a
    //field it does not consider secret that still reads like one
    private static final Pattern SECRETISH = Pattern.compile("(?i)(password|secret|token|_key$)");

    private static final int MAX_DETAILS = 8;

    public AlertManagerRouting parse(String alertmanagerName, String yaml) throws Exception {
        //SafeConstructor: this yaml came off the network, so no arbitrary types
        Yaml parser = new Yaml(new SafeConstructor());
        Object loaded = parser.load(yaml);
        if (!(loaded instanceof Map)) {
            throw new IllegalArgumentException("alertmanager config was not a yaml mapping");
        }
        Map<String, Object> root = asMap(loaded);

        AlertManagerRouting routing = new AlertManagerRouting(alertmanagerName);
        Set<String> used = new LinkedHashSet<>();

        Map<String, Object> routeYaml = asMap(root.get("route"));
        if (routeYaml != null) {
            routing.setRoute(buildRoute(routeYaml, "0", null, used));
        }
        routing.setReceivers(buildReceivers(root.get("receivers"), used));
        routing.setTimeIntervals(intervalNames(root));
        routing.setAvailable(true);
        routing.setStale(false);
        routing.setFetchedAt(System.currentTimeMillis());
        return routing;
    }

    private RouteNode buildRoute(Map<String, Object> yaml, String id, RouteNode parent, Set<String> used) {
        RouteNode node = new RouteNode();
        node.setId(id);
        node.setMatchers(buildMatchers(yaml));
        node.setContinueOnMatch(Boolean.TRUE.equals(yaml.get("continue")));

        Map<String, Boolean> inherited = new LinkedHashMap<>();

        //receiver, group_by and the three timings cascade from the parent unless
        //this node sets its own. mute/active intervals are deliberately left alone:
        //their inheritance has moved between alertmanager releases, and claiming
        //inheritance we are unsure of is worse than showing only what is set here.
        String receiver = str(yaml.get("receiver"));
        inherited.put("receiver", receiver == null && parent != null);
        node.setReceiver(receiver != null ? receiver : (parent != null ? parent.getReceiver() : null));
        if (node.getReceiver() != null) {
            used.add(node.getReceiver());
        }

        //only claim inheritance when the parent actually had something to give,
        //otherwise a config that never sets group_by reads as inherited from nothing
        List<String> groupBy = strList(yaml.get("group_by"));
        boolean parentHasGroupBy = parent != null && !parent.getGroupBy().isEmpty();
        inherited.put("groupBy", groupBy.isEmpty() && parentHasGroupBy);
        node.setGroupBy(!groupBy.isEmpty() ? groupBy : (parentHasGroupBy ? parent.getGroupBy() : groupBy));

        node.setGroupWait(inheritString(yaml, "group_wait", parent == null ? null : parent.getGroupWait(), parent, inherited, "groupWait"));
        node.setGroupInterval(inheritString(yaml, "group_interval", parent == null ? null : parent.getGroupInterval(), parent, inherited, "groupInterval"));
        node.setRepeatInterval(inheritString(yaml, "repeat_interval", parent == null ? null : parent.getRepeatInterval(), parent, inherited, "repeatInterval"));

        node.setMuteTimeIntervals(strList(yaml.get("mute_time_intervals")));
        node.setActiveTimeIntervals(strList(yaml.get("active_time_intervals")));
        node.setInherited(inherited);

        List<RouteNode> children = new ArrayList<>();
        Object routes = yaml.get("routes");
        if (routes instanceof List) {
            int i = 0;
            for (Object child : (List<?>) routes) {
                Map<String, Object> childMap = asMap(child);
                if (childMap != null) {
                    children.add(buildRoute(childMap, id + "." + i, node, used));
                    i++;
                }
            }
        }
        node.setRoutes(children);
        return node;
    }

    private String inheritString(Map<String, Object> yaml, String key, String parentValue,
                                 RouteNode parent, Map<String, Boolean> inherited, String jsonName) {
        String own = str(yaml.get(key));
        inherited.put(jsonName, own == null && parent != null && parentValue != null);
        return (own != null) ? own : parentValue;
    }

    List<RouteMatcher> buildMatchers(Map<String, Object> yaml) {
        List<RouteMatcher> out = new ArrayList<>();
        //modern form: a list of strings like jira="true"
        Object matchers = yaml.get("matchers");
        if (matchers instanceof List) {
            for (Object m : (List<?>) matchers) {
                if (m != null) {
                    out.add(parseMatcher(String.valueOf(m)));
                }
            }
        }
        //legacy forms, still accepted by alertmanager
        Map<String, Object> match = asMap(yaml.get("match"));
        if (match != null) {
            for (Map.Entry<String, Object> e : match.entrySet()) {
                out.add(new RouteMatcher(e.getKey(), "=", str(e.getValue()), e.getKey() + "=\"" + str(e.getValue()) + "\""));
            }
        }
        Map<String, Object> matchRe = asMap(yaml.get("match_re"));
        if (matchRe != null) {
            for (Map.Entry<String, Object> e : matchRe.entrySet()) {
                out.add(new RouteMatcher(e.getKey(), "=~", str(e.getValue()), e.getKey() + "=~\"" + str(e.getValue()) + "\""));
            }
        }
        return out;
    }

    //package visible so the parsing of alertmanager's matcher syntax can be tested directly
    static RouteMatcher parseMatcher(String raw) {
        Matcher m = MATCHER.matcher(raw);
        if (m.matches()) {
            return new RouteMatcher(m.group(1), m.group(2), m.group(3), raw);
        }
        //unsplittable: keep alertmanager's own text rather than inventing a reading
        return new RouteMatcher(null, null, null, raw);
    }

    private List<ReceiverInfo> buildReceivers(Object raw, Set<String> used) {
        List<ReceiverInfo> out = new ArrayList<>();
        if (!(raw instanceof List)) {
            return out;
        }
        for (Object entry : (List<?>) raw) {
            Map<String, Object> map = asMap(entry);
            if (map == null) {
                continue;
            }
            ReceiverInfo info = new ReceiverInfo(str(map.get("name")));
            info.setActions(buildActions(map));
            info.setUsed(used.contains(info.getName()));
            out.add(info);
        }
        return out;
    }

    private List<ReceiverAction> buildActions(Map<String, Object> receiver) {
        List<ReceiverAction> actions = new ArrayList<>();
        for (Map.Entry<String, Object> entry : receiver.entrySet()) {
            String key = entry.getKey();
            if (!key.endsWith("_configs") || !(entry.getValue() instanceof List)) {
                continue;
            }
            String type = key.substring(0, key.length() - "_configs".length());
            for (Object cfg : (List<?>) entry.getValue()) {
                Map<String, Object> config = asMap(cfg);
                if (config == null) {
                    continue;
                }
                String targetKey = targetKeyOf(type, config);
                Object sendResolved = config.get("send_resolved");
                ReceiverAction action = new ReceiverAction(type, str(config.get(targetKey)),
                        (sendResolved instanceof Boolean) ? (Boolean) sendResolved : null);
                action.setDetails(detailsOf(config, targetKey));
                actions.add(action);
            }
        }
        return actions;
    }

    private static String targetKeyOf(String type, Map<String, Object> config) {
        String[] preferred = TARGET_KEYS.get(type);
        if (preferred != null) {
            for (String key : preferred) {
                if (config.get(key) != null) {
                    return key;
                }
            }
        }
        for (String key : TARGET_FALLBACK) {
            if (config.get(key) != null) {
                return key;
            }
        }
        return null;
    }

    //the remaining scalars, which drops nested blocks like http_config and tls_config
    //that say nothing about where a notification goes
    private Map<String, String> detailsOf(Map<String, Object> config, String targetKey) {
        Map<String, String> details = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : config.entrySet()) {
            if (details.size() >= MAX_DETAILS) {
                break;
            }
            String key = e.getKey();
            Object value = e.getValue();
            if (key.equals(targetKey) || key.equals("send_resolved")) {
                continue;
            }
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                continue;
            }
            //alertmanager redacts what it considers secret; skip anything else that
            //reads like a credential rather than passing it to every logged in user
            if (SECRETISH.matcher(key).find() && !"<secret>".equals(String.valueOf(value))) {
                continue;
            }
            details.put(key, String.valueOf(value));
        }
        return details;
    }

    //routes reference intervals by name; the schedules themselves are not shown
    private List<String> intervalNames(Map<String, Object> root) {
        List<String> names = new ArrayList<>();
        for (String key : new String[]{"time_intervals", "mute_time_intervals"}) {
            Object list = root.get(key);
            if (!(list instanceof List)) {
                continue;
            }
            for (Object entry : (List<?>) list) {
                Map<String, Object> map = asMap(entry);
                String name = (map == null) ? null : str(map.get("name"));
                if (name != null && !names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : null;
    }

    private static String str(Object o) {
        return (o == null) ? null : String.valueOf(o);
    }

    private static List<String> strList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List) {
            for (Object e : (List<?>) o) {
                if (e != null) {
                    out.add(String.valueOf(e));
                }
            }
        }
        return out;
    }
}
