package com.costco.scraper.config;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.Duration;
import java.util.List;
import java.util.Random;

@Slf4j
@Configuration
public class SeleniumConfig {

    @Value("${selenium.headless:true}")
    private boolean headless;

    @Value("${selenium.window-size:1920x1080}")
    private String windowSize;

    @Value("${selenium.user-agents}")
    private List<String> userAgents;

    @Value("${scraper.timeout.page-load-seconds:30}")
    private int pageLoadTimeout;

    private final Random random = new Random();

    @Bean
    @Scope("prototype")
    public WebDriver webDriver() {
        log.info("Initializing Chrome WebDriver...");

        // Setup ChromeDriver using WebDriverManager
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = createChromeOptions();
        WebDriver driver = new ChromeDriver(options);

        // Set timeouts
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(pageLoadTimeout));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        log.info("Chrome WebDriver initialized successfully");
        return driver;
    }

    private ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();

        // Headless mode for server deployment
        if (headless) {
            options.addArguments("--headless=new");
        }

        // Required for running in Docker/Cloud Run
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");

        // Window size
        options.addArguments("--window-size=" + windowSize);

        // Random User-Agent
        String userAgent = getRandomUserAgent();
        options.addArguments("--user-agent=" + userAgent);
        log.debug("Using User-Agent: {}", userAgent);

        // Additional anti-detection options
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        // Performance optimizations
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");

        // Language settings for Taiwan
        options.addArguments("--lang=zh-TW");
        options.addArguments("--accept-lang=zh-TW,zh;q=0.9,en;q=0.8");

        return options;
    }

    private String getRandomUserAgent() {
        if (userAgents == null || userAgents.isEmpty()) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        }
        return userAgents.get(random.nextInt(userAgents.size()));
    }
}
