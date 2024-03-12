package gmdev.platform.alertviewer.server;

import gmdev.platform.alertviewer.data.alert.AlertManagerEntryRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Initializer {

    private static final Logger log = LoggerFactory.getLogger(Initializer.class);

    @Autowired
    AlertManagerEntryRepo repo;

    public void init() {


    }

}
