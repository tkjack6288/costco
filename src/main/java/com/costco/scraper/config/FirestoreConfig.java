package com.costco.scraper.config;

import com.google.cloud.spring.data.firestore.repository.config.EnableReactiveFirestoreRepositories;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableReactiveFirestoreRepositories(basePackages = "com.costco.scraper.repository")
public class FirestoreConfig {
    // Firestore configuration is handled via application.yml
    // This class enables reactive Firestore repositories
}
