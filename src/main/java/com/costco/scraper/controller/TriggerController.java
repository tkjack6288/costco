package com.costco.scraper.controller;

import com.costco.scraper.service.FirestoreService;
import com.costco.scraper.service.ScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequestMapping("/api/scraper")
@RequiredArgsConstructor
public class TriggerController {

    private final ScraperService scraperService;
    private final FirestoreService firestoreService;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * Trigger scraping via HTTP POST (for Cloud Scheduler)
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerScrape() {
        log.info("Received scrape trigger request");

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Scraping is already in progress");
            return ResponseEntity.status(429)
                    .body(createResponse("error", "Scraping is already in progress", null));
        }

        try {
            // Run scraping asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("Starting async scraping task");
                    ScraperService.ScrapingResult result = scraperService.scrapeAll();

                    log.info("Scraping completed. Products: {}, Errors: {}, Duration: {}s",
                            result.getProducts().size(),
                            result.getErrors().size(),
                            result.getDurationSeconds());
                } catch (Exception e) {
                    log.error("Async scraping failed: {}", e.getMessage(), e);
                } finally {
                    isRunning.set(false);
                }
            });

            return ResponseEntity.accepted()
                    .body(createResponse("accepted", "Scraping task started", null));

        } catch (Exception e) {
            isRunning.set(false);
            log.error("Failed to start scraping: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(createResponse("error", e.getMessage(), null));
        }
    }

    /**
     * Trigger scraping and wait for completion (synchronous)
     */
    @PostMapping("/trigger/sync")
    public ResponseEntity<Map<String, Object>> triggerScrapeSync() {
        log.info("Received synchronous scrape trigger request");

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Scraping is already in progress");
            return ResponseEntity.status(429)
                    .body(createResponse("error", "Scraping is already in progress", null));
        }

        try {
            ScraperService.ScrapingResult result = scraperService.scrapeAll();

            Map<String, Object> data = new HashMap<>();
            data.put("productsScraped", result.getProducts().size());
            data.put("errors", result.getErrors());
            data.put("durationSeconds", result.getDurationSeconds());
            data.put("startTime", result.getStartTime().toString());
            data.put("endTime", result.getEndTime().toString());

            return ResponseEntity.ok()
                    .body(createResponse("success", "Scraping completed", data));

        } catch (Exception e) {
            log.error("Scraping failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(createResponse("error", e.getMessage(), null));
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * Trigger scraping for a specific category
     */
    @PostMapping("/trigger/category")
    public ResponseEntity<Map<String, Object>> triggerCategoryScrape(
            @RequestParam String url,
            @RequestParam(defaultValue = "Category") String name) {

        log.info("Received category scrape trigger request for: {}", name);

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Scraping is already in progress");
            return ResponseEntity.status(429)
                    .body(createResponse("error", "Scraping is already in progress", null));
        }

        try {
            ScraperService.ScrapingResult result = scraperService.scrapeCategory(url, name);

            Map<String, Object> data = new HashMap<>();
            data.put("category", name);
            data.put("productsScraped", result.getProducts().size());
            data.put("errors", result.getErrors());
            data.put("durationSeconds", result.getDurationSeconds());

            return ResponseEntity.ok()
                    .body(createResponse("success", "Category scraping completed", data));

        } catch (Exception e) {
            log.error("Category scraping failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(createResponse("error", e.getMessage(), null));
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * Check scraping status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> data = new HashMap<>();
        data.put("isRunning", isRunning.get());
        data.put("productCount", firestoreService.getProductCount());
        data.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok()
                .body(createResponse("success", "Status retrieved", data));
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok()
                .body(createResponse("success", "Service is healthy", data));
    }

    private Map<String, Object> createResponse(String status, String message, Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status);
        response.put("message", message);
        response.put("timestamp", Instant.now().toString());
        if (data != null) {
            response.put("data", data);
        }
        return response;
    }
}
