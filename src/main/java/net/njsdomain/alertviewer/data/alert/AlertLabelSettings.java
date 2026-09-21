package net.njsdomain.alertviewer.data.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Hands Alert the label name it should treat as an alternative spelling of
 * "environment". Alerts are deserialized by jackson, so the setting has to be pushed
 * onto the class at startup rather than injected into each one.
 */
@Component
public class AlertLabelSettings {

    private static final Logger log = LoggerFactory.getLogger(AlertLabelSettings.class);

    @Autowired
    Environment env;

    @PostConstruct
    private void init() {
        Alert.setAlternativeEnvironmentLabel(env.getProperty("environment.label.alternative", "gm_instance"));
        String label = Alert.getAlternativeEnvironmentLabel();
        if (label == null) {
            log.info("No alternative environment label configured, alerts must carry 'environment' themselves");
        } else {
            log.info("Alerts labelled '" + label + "' and nothing else will be treated as that environment");
        }
    }
}
