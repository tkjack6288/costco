package com.costco.scraper.config;

import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationInitializer implements ApplicationListener<ApplicationReadyEvent>, CommandLineRunner {

    private final Environment environment;
    private final Firestore firestore;

    @Value("${spring.application.name:costco-scraper}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${scraper.base-url}")
    private String scraperBaseUrl;

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("  {} - Initializing", applicationName);
        log.info("========================================");

        // Log active profiles
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            log.info("Active Profiles: {}", Arrays.toString(activeProfiles));
        } else {
            log.info("Active Profiles: default");
        }

        // Verify Firestore connection
        verifyFirestoreConnection();
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
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
            log.warn("Could not determine host address: {}", e.getMessage());
        }
    }

    private void verifyFirestoreConnection() {
        try {
            String projectId = firestore.getOptions().getProjectId();
            log.info("Firestore Project ID: {}", projectId);

            // Try to access Firestore to verify connection
            firestore.listCollections().forEach(collection -> {
                log.debug("Found collection: {}", collection.getId());
            });

            log.info("Firestore connection verified successfully");
        } catch (Exception e) {
            log.warn("Could not verify Firestore connection: {}", e.getMessage());
            log.warn("Scraping will continue, but data may not be saved.");
        }
    }
}
