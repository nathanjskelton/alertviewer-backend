package net.njsdomain.alertviewer.server;

import net.njsdomain.alertviewer.data.AlertManagerEntryRepo;
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
