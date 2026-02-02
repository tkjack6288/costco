package com.costco.scraper.scheduler;

import com.costco.scraper.service.ScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScraperScheduler {

    private final ScraperService scraperService;

    /**
     * Scheduled task that runs every day at 3:00 AM
     * Cron expression: second minute hour day month weekday
     */
    @Scheduled(cron = "${scraper.schedule.cron:0 0 3 * * *}")
    public void scheduledScrape() {
        String startTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        log.info("Starting scheduled scraping task at {}", startTime);

        try {
            ScraperService.ScrapingResult result = scraperService.scrapeAll();

            log.info("Scheduled scraping completed. Products: {}, Errors: {}, Duration: {}s",
                    result.getProducts().size(),
                    result.getErrors().size(),
                    result.getDurationSeconds());

            if (!result.getErrors().isEmpty()) {
                log.warn("Scraping completed with {} errors:", result.getErrors().size());
                result.getErrors().forEach(error -> log.warn(" - {}", error));
            }

        } catch (Exception e) {
            log.error("Scheduled scraping failed with exception: {}", e.getMessage(), e);
        }
    }
}
