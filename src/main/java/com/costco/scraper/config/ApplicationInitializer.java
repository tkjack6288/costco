package com.costco.scraper.config;

import com.google.cloud.firestore.Firestore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Arrays;

@Slf4j
@Component
public class ApplicationInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final Environment environment;

    @Autowired(required = false)
    private Firestore firestore;

    @Value("${spring.application.name:costco-scraper}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${scraper.base-url:https://www.costco.com.tw}")
    private String scraperBaseUrl;

    public ApplicationInitializer(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("========================================");
        log.info("  {} - Starting", applicationName);
        log.info("========================================");

        // Log active profiles
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            log.info("Active Profiles: {}", Arrays.toString(activeProfiles));
        } else {
            log.info("Active Profiles: default");
        }

        // Verify Firestore connection (non-blocking)
        verifyFirestoreConnection();

        try {
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            log.info("========================================");
            log.info("  {} - Ready!", applicationName);
            log.info("========================================");
            log.info("Local:    http://localhost:{}", serverPort);
            log.info("Network:  http://{}:{}", hostAddress, serverPort);
            log.info("Health:   http://localhost:{}/actuator/health", serverPort);
            log.info("Trigger:  POST http://localhost:{}/api/scraper/trigger", serverPort);
            log.info("Target:   {}", scraperBaseUrl);
            log.info("========================================");
        } catch (Exception e) {
            log.info("Application started on port {}", serverPort);
        }
    }

    private void verifyFirestoreConnection() {
        if (firestore == null) {
            log.warn("Firestore not configured - data will not be persisted");
            return;
        }

        try {
            String projectId = firestore.getOptions().getProjectId();
            log.info("Firestore Project ID: {}", projectId);
            log.info("Firestore connection available");
        } catch (Exception e) {
            log.warn("Could not verify Firestore connection: {}", e.getMessage());
            log.warn("Scraping will continue, but data may not be saved.");
        }
    }
}
