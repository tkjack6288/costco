package com.costco.scraper.service;

import com.costco.scraper.model.Product;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ScraperService {

    private final ObjectProvider<WebDriver> webDriverProvider;
    private final FirestoreService firestoreService;

    @Value("${scraper.base-url}")
    private String baseUrl;

    @Value("${scraper.delay.min-ms:3000}")
    private int minDelayMs;

    @Value("${scraper.delay.max-ms:5000}")
    private int maxDelayMs;

    @Value("${scraper.page.max-pages:100}")
    private int maxPages;

    @Value("${scraper.timeout.element-wait-seconds:10}")
    private int elementWaitSeconds;

    private WebDriver driver;
    private WebDriverWait wait;

    public ScraperService(ObjectProvider<WebDriver> webDriverProvider, FirestoreService firestoreService) {
        this.webDriverProvider = webDriverProvider;
        this.firestoreService = firestoreService;
    }

    /**
     * Main scraping method - scrapes all categories
     */
    public ScrapingResult scrapeAll() {
        log.info("Starting full scrape of Costco Taiwan website");
        ScrapingResult result = new ScrapingResult();
        result.setStartTime(Instant.now());

        try {
            initializeDriver();

            // Get all category URLs
            List<CategoryInfo> categories = scrapeCategories();
            log.info("Found {} categories to scrape", categories.size());

            for (CategoryInfo category : categories) {
                try {
                    log.info("Scraping category: {}", category.getName());
                    List<Product> products = scrapeCategoryProducts(category);
                    result.addProducts(products);
                    log.info("Scraped {} products from category: {}", products.size(), category.getName());
                } catch (Exception e) {
                    log.error("Error scraping category {}: {}", category.getName(), e.getMessage());
                    result.addError(category.getName() + ": " + e.getMessage());
                }
            }

            // Save all products to Firestore
            if (!result.getProducts().isEmpty()) {
                firestoreService.saveProducts(result.getProducts());
            }

        } catch (Exception e) {
            log.error("Critical error during scraping: {}", e.getMessage(), e);
            result.addError("Critical: " + e.getMessage());
        } finally {
            closeDriver();
            result.setEndTime(Instant.now());
        }

        log.info("Scraping completed. Total products: {}, Errors: {}",
                result.getProducts().size(), result.getErrors().size());
        return result;
    }

    /**
     * Scrape a specific category by URL
     */
    public ScrapingResult scrapeCategory(String categoryUrl, String categoryName) {
        log.info("Starting scrape of category: {}", categoryName);
        ScrapingResult result = new ScrapingResult();
        result.setStartTime(Instant.now());

        try {
            initializeDriver();

            CategoryInfo category = new CategoryInfo(categoryName, categoryUrl, null);
            List<Product> products = scrapeCategoryProducts(category);
            result.addProducts(products);

            // Save products to Firestore
            if (!products.isEmpty()) {
                firestoreService.saveProducts(products);
            }

        } catch (Exception e) {
            log.error("Error scraping category {}: {}", categoryName, e.getMessage(), e);
            result.addError(e.getMessage());
        } finally {
            closeDriver();
            result.setEndTime(Instant.now());
        }

        return result;
    }

    private void initializeDriver() {
        if (driver == null) {
            driver = webDriverProvider.getObject();
            wait = new WebDriverWait(driver, Duration.ofSeconds(elementWaitSeconds));
            log.info("WebDriver initialized");
        }
    }

    private void closeDriver() {
        if (driver != null) {
            try {
                driver.quit();
                log.info("WebDriver closed");
            } catch (Exception e) {
                log.warn("Error closing WebDriver: {}", e.getMessage());
            } finally {
                driver = null;
                wait = null;
            }
        }
    }

    /**
     * Scrape all category links from the main category page
     */
    private List<CategoryInfo> scrapeCategories() {
        List<CategoryInfo> categories = new ArrayList<>();

        try {
            String categoriesUrl = baseUrl + "/c/all-categories";
            log.info("Navigating to categories page: {}", categoriesUrl);
            driver.get(categoriesUrl);
            randomDelay();

            // Wait for category links to load
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("a[href*='/c/']")));

            // Find all category links
            List<WebElement> categoryLinks = driver.findElements(
                    By.cssSelector(".category-tile a, .category-card a, a[href*='/c/']"));

            Set<String> seenUrls = new HashSet<>();
            for (WebElement link : categoryLinks) {
                try {
                    String href = link.getAttribute("href");
                    String name = link.getText().trim();

                    if (href != null && !href.isEmpty() && href.contains("/c/")
                            && !href.contains("/all-categories") && !seenUrls.contains(href)) {
                        seenUrls.add(href);

                        // Extract parent category if available
                        String parentCategory = null;
                        try {
                            WebElement parent = link.findElement(By.xpath("./ancestor::*[contains(@class,'category')]"));
                            WebElement header = parent.findElement(By.cssSelector("h2, h3, .category-title"));
                            parentCategory = header.getText().trim();
                        } catch (Exception ignored) {
                        }

                        if (name.isEmpty()) {
                            // Try to get name from image alt or title
                            try {
                                WebElement img = link.findElement(By.tagName("img"));
                                name = img.getAttribute("alt");
                            } catch (Exception ignored) {
                            }
                        }

                        if (!name.isEmpty()) {
                            categories.add(new CategoryInfo(name, href, parentCategory));
                            log.debug("Found category: {} - {}", name, href);
                        }
                    }
                } catch (StaleElementReferenceException e) {
                    log.warn("Stale element, skipping category link");
                }
            }

        } catch (Exception e) {
            log.error("Error scraping categories: {}", e.getMessage());
        }

        return categories;
    }

    /**
     * Scrape all products from a category (handles pagination)
     */
    private List<Product> scrapeCategoryProducts(CategoryInfo category) {
        List<Product> products = new ArrayList<>();
        int currentPage = 1;
        boolean hasMorePages = true;

        String categoryUrl = category.getUrl();
        if (!categoryUrl.startsWith("http")) {
            categoryUrl = baseUrl + categoryUrl;
        }

        while (hasMorePages && currentPage <= maxPages) {
            try {
                String pageUrl = currentPage == 1 ? categoryUrl : categoryUrl + "?page=" + currentPage;
                log.info("Scraping page {} of category: {}", currentPage, category.getName());

                driver.get(pageUrl);
                randomDelay();

                // Wait for product grid to load
                try {
                    wait.until(ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector(".product-tile, .product-card, .product-item, [data-product-id]")));
                } catch (TimeoutException e) {
                    log.info("No products found on page {}, stopping pagination", currentPage);
                    break;
                }

                // Scroll to load lazy-loaded content
                scrollToBottom();

                // Find all product elements on the page
                List<WebElement> productElements = driver.findElements(
                        By.cssSelector(".product-tile, .product-card, .product-item, [data-product-id]"));

                if (productElements.isEmpty()) {
                    log.info("No products found on page {}", currentPage);
                    break;
                }

                log.info("Found {} product elements on page {}", productElements.size(), currentPage);

                for (WebElement productElement : productElements) {
                    try {
                        Product product = parseProductElement(productElement, category);
                        if (product != null && product.getProductId() != null) {
                            products.add(product);
                        }
                    } catch (StaleElementReferenceException e) {
                        log.warn("Stale element, skipping product");
                    } catch (Exception e) {
                        log.warn("Error parsing product: {}", e.getMessage());
                    }
                }

                // Check if there's a next page
                hasMorePages = hasNextPage();
                currentPage++;

            } catch (Exception e) {
                log.error("Error scraping page {} of category {}: {}",
                        currentPage, category.getName(), e.getMessage());
                break;
            }
        }

        return products;
    }

    /**
     * Parse a product element into a Product object
     */
    private Product parseProductElement(WebElement productElement, CategoryInfo category) {
        Product.ProductBuilder builder = Product.builder();

        // Extract product ID
        String productId = extractProductId(productElement);
        if (productId == null || productId.isEmpty()) {
            return null;
        }
        builder.productId(productId);

        // Extract product name
        String name = extractText(productElement,
                ".product-title, .product-name, h3, h4, [data-product-name]");
        builder.name(name);

        // Extract price
        String priceText = extractText(productElement,
                ".price, .product-price, .sale-price, [data-price]");
        Double price = parsePrice(priceText);
        builder.price(price);

        // Extract original price (if on sale)
        String originalPriceText = extractText(productElement,
                ".original-price, .was-price, .strikethrough-price, del");
        Double originalPrice = parsePrice(originalPriceText);
        builder.originalPrice(originalPrice);

        // Extract discount info
        String discount = extractText(productElement,
                ".discount, .savings, .promotion, .badge");
        builder.discount(discount);

        // Extract image URL
        String imageUrl = extractAttribute(productElement,
                "img, .product-image img", "src");
        if (imageUrl == null || imageUrl.isEmpty()) {
            imageUrl = extractAttribute(productElement,
                    "img, .product-image img", "data-src");
        }
        builder.imageUrl(imageUrl);

        // Extract product URL
        String productUrl = extractAttribute(productElement, "a", "href");
        if (productUrl != null && !productUrl.startsWith("http")) {
            productUrl = baseUrl + productUrl;
        }
        builder.productUrl(productUrl);

        // Set category info
        builder.category(category.getParentCategory() != null ?
                category.getParentCategory() : category.getName());
        builder.subCategory(category.getName());

        // Check availability
        boolean available = !hasElement(productElement, ".out-of-stock, .unavailable");
        builder.availability(available);

        // Set timestamps
        builder.scrapedAt(Instant.now());

        return builder.build();
    }

    /**
     * Extract product ID from element
     */
    private String extractProductId(WebElement element) {
        // Try data attributes first
        String productId = element.getAttribute("data-product-id");
        if (productId != null && !productId.isEmpty()) {
            return productId;
        }

        productId = element.getAttribute("data-sku");
        if (productId != null && !productId.isEmpty()) {
            return productId;
        }

        productId = element.getAttribute("data-item-id");
        if (productId != null && !productId.isEmpty()) {
            return productId;
        }

        // Try to extract from URL
        try {
            WebElement link = element.findElement(By.cssSelector("a[href*='/p/']"));
            String href = link.getAttribute("href");
            Pattern pattern = Pattern.compile("/p/([^/\\?]+)");
            Matcher matcher = pattern.matcher(href);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception ignored) {
        }

        // Try ID attribute
        String id = element.getAttribute("id");
        if (id != null && !id.isEmpty()) {
            return id;
        }

        return null;
    }

    /**
     * Extract text from an element using multiple selectors
     */
    private String extractText(WebElement parent, String selectors) {
        for (String selector : selectors.split(",")) {
            try {
                WebElement element = parent.findElement(By.cssSelector(selector.trim()));
                String text = element.getText().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            } catch (NoSuchElementException ignored) {
            }
        }
        return null;
    }

    /**
     * Extract attribute from an element
     */
    private String extractAttribute(WebElement parent, String selectors, String attribute) {
        for (String selector : selectors.split(",")) {
            try {
                WebElement element = parent.findElement(By.cssSelector(selector.trim()));
                String value = element.getAttribute(attribute);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            } catch (NoSuchElementException ignored) {
            }
        }
        return null;
    }

    /**
     * Check if an element exists
     */
    private boolean hasElement(WebElement parent, String selectors) {
        for (String selector : selectors.split(",")) {
            try {
                parent.findElement(By.cssSelector(selector.trim()));
                return true;
            } catch (NoSuchElementException ignored) {
            }
        }
        return false;
    }

    /**
     * Parse price string to Double
     */
    private Double parsePrice(String priceText) {
        if (priceText == null || priceText.isEmpty()) {
            return null;
        }

        try {
            // Remove currency symbols and commas
            String cleaned = priceText
                    .replaceAll("[^0-9.]", "")
                    .trim();

            if (!cleaned.isEmpty()) {
                return Double.parseDouble(cleaned);
            }
        } catch (NumberFormatException e) {
            log.warn("Could not parse price: {}", priceText);
        }
        return null;
    }

    /**
     * Check if there's a next page
     */
    private boolean hasNextPage() {
        try {
            // Look for next page button or link
            List<WebElement> nextButtons = driver.findElements(
                    By.cssSelector(".pagination .next:not(.disabled), " +
                            "a[aria-label='Next'], " +
                            "button[aria-label='Next page']:not([disabled]), " +
                            ".page-next:not(.disabled)"));

            return !nextButtons.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Scroll to bottom of page to load lazy content
     */
    private void scrollToBottom() {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Get initial page height
            long lastHeight = (long) js.executeScript("return document.body.scrollHeight");

            int scrollAttempts = 0;
            while (scrollAttempts < 10) {
                // Scroll down
                js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(1000);

                // Get new height
                long newHeight = (long) js.executeScript("return document.body.scrollHeight");

                if (newHeight == lastHeight) {
                    break;
                }
                lastHeight = newHeight;
                scrollAttempts++;
            }

            // Scroll back to top
            js.executeScript("window.scrollTo(0, 0);");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Error during scroll: {}", e.getMessage());
        }
    }

    /**
     * Add random delay between requests
     */
    private void randomDelay() {
        try {
            int delay = ThreadLocalRandom.current().nextInt(minDelayMs, maxDelayMs + 1);
            log.debug("Waiting {}ms before next request", delay);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Category information holder
     */
    private static class CategoryInfo {
        private final String name;
        private final String url;
        private final String parentCategory;

        public CategoryInfo(String name, String url, String parentCategory) {
            this.name = name;
            this.url = url;
            this.parentCategory = parentCategory;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }

        public String getParentCategory() {
            return parentCategory;
        }
    }

    /**
     * Scraping result holder
     */
    public static class ScrapingResult {
        private List<Product> products = new ArrayList<>();
        private List<String> errors = new ArrayList<>();
        private Instant startTime;
        private Instant endTime;

        public List<Product> getProducts() {
            return products;
        }

        public void addProducts(List<Product> newProducts) {
            this.products.addAll(newProducts);
        }

        public List<String> getErrors() {
            return errors;
        }

        public void addError(String error) {
            this.errors.add(error);
        }

        public Instant getStartTime() {
            return startTime;
        }

        public void setStartTime(Instant startTime) {
            this.startTime = startTime;
        }

        public Instant getEndTime() {
            return endTime;
        }

        public void setEndTime(Instant endTime) {
            this.endTime = endTime;
        }

        public long getDurationSeconds() {
            if (startTime != null && endTime != null) {
                return Duration.between(startTime, endTime).getSeconds();
            }
            return 0;
        }
    }
}
