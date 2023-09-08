package gmdev.platform.logviewer.server;

import gmdev.platform.logviewer.data.AlertManagerRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Initializer {

    private static final Logger log = LoggerFactory.getLogger(Initializer.class);

    @Autowired
    AlertManagerRepo repo;

    public void init() {


    }

}
