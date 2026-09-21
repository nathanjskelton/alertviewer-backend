package net.njsdomain.alertviewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableMongoRepositories
@EnableAsync
public class AlertViewer {
    public static void main(String[] args) {
        //Everything in this tool is UTC: alertmanager speaks it, every LocalDateTime
        //held here is a UTC wall clock, and the UI shows UTC. Pin the JVM to it so
        //the host's zone cannot leak in -- through a stray now(), or through the
        //LocalDateTime <-> Date conversion mongo does with the default zone.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(AlertViewer.class, args);
    }
}
