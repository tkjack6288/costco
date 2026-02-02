package com.costco.scraper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulerConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scraper-scheduler-");
        scheduler.setErrorHandler(throwable -> {
            // Log error but don't stop the scheduler
            System.err.println("Scheduler error: " + throwable.getMessage());
        });
        return scheduler;
    }
}
