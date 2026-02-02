# Costco Taiwan Product Scraper

A Java Spring Boot application that scrapes product information from the Costco Taiwan website and stores it in Google Cloud Firestore.

## Features

- **Selenium WebDriver** with Headless Chrome for JavaScript rendering
- **Anti-detection measures**: Random User-Agent, request delays, cookie management
- **Google Cloud Firestore** integration for data persistence
- **Scheduled scraping** via Spring Scheduler
- **HTTP API** for manual triggering (Cloud Scheduler compatible)
- **Docker** containerization with Chrome included
- **GitHub Actions** CI/CD pipeline for Cloud Run deployment

## Project Structure

```
costco-scraper/
├── src/main/java/com/costco/scraper/
│   ├── CostcoScraperApplication.java      # Spring Boot main class
│   ├── config/
│   │   ├── FirestoreConfig.java           # Firestore configuration
│   │   ├── SeleniumConfig.java            # Selenium WebDriver configuration
│   │   └── SchedulerConfig.java           # Scheduler configuration
│   ├── model/
│   │   └── Product.java                   # Product data model
│   ├── service/
│   │   ├── ScraperService.java            # Core scraping logic
│   │   └── FirestoreService.java          # Firestore operations
│   ├── scheduler/
│   │   └── ScraperScheduler.java          # Scheduled tasks
│   └── controller/
│       └── TriggerController.java         # HTTP API endpoints
├── src/main/resources/
│   └── application.yml                    # Application configuration
├── Dockerfile                             # Docker image configuration
├── pom.xml                                # Maven dependencies
└── .github/workflows/
    └── deploy.yml                         # GitHub Actions CI/CD
```

## Prerequisites

- Java 17+
- Maven 3.8+
- Google Cloud Project with Firestore enabled
- Docker (for containerization)

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `GCP_PROJECT_ID` | Google Cloud Project ID | - |
| `PORT` | Server port | 8080 |

### Application Properties

Edit `src/main/resources/application.yml`:

```yaml
scraper:
  base-url: https://www.costco.com.tw
  delay:
    min-ms: 3000    # Minimum delay between requests
    max-ms: 5000    # Maximum delay between requests
  page:
    max-pages: 100  # Maximum pages to scrape per category
```

## Running Locally

1. **Set up Google Cloud credentials:**
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
   export GCP_PROJECT_ID=your-project-id
   ```

2. **Build and run:**
   ```bash
   mvn clean package
   java -jar target/costco-scraper-1.0.0.jar
   ```

3. **Or with Maven:**
   ```bash
   mvn spring-boot:run
   ```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/scraper/trigger` | Trigger async scraping |
| POST | `/api/scraper/trigger/sync` | Trigger sync scraping |
| POST | `/api/scraper/trigger/category` | Scrape specific category |
| GET | `/api/scraper/status` | Get scraping status |
| GET | `/api/scraper/health` | Health check |
| GET | `/actuator/health` | Spring Actuator health |

## Docker

### Build image:
```bash
docker build -t costco-scraper .
```

### Run container:
```bash
docker run -p 8080:8080 \
  -e GCP_PROJECT_ID=your-project-id \
  -v /path/to/credentials.json:/app/credentials.json \
  -e GOOGLE_APPLICATION_CREDENTIALS=/app/credentials.json \
  costco-scraper
```

## Deployment to Cloud Run

### GitHub Actions (Recommended)

1. Add secrets to your GitHub repository:
   - `GCP_PROJECT_ID`: Your Google Cloud Project ID
   - `GCP_SA_KEY`: Service account JSON key

2. Push to `main` branch to trigger deployment.

### Manual Deployment

```bash
# Build and push image
gcloud builds submit --tag gcr.io/$PROJECT_ID/costco-scraper

# Deploy to Cloud Run
gcloud run deploy costco-scraper \
  --image gcr.io/$PROJECT_ID/costco-scraper \
  --platform managed \
  --region asia-east1 \
  --memory 2Gi \
  --cpu 2 \
  --timeout 3600 \
  --min-instances 0 \
  --max-instances 1
```

## Cloud Scheduler Setup

Create a Cloud Scheduler job to trigger daily scraping:

```bash
gcloud scheduler jobs create http costco-scraper-daily \
  --location=asia-east1 \
  --schedule="0 3 * * *" \
  --uri="https://your-cloud-run-url/api/scraper/trigger" \
  --http-method=POST \
  --time-zone="Asia/Taipei"
```

## Data Model (Firestore)

```
Collection: products
Document: {productId}
Fields:
  - productId: String
  - name: String
  - price: Double
  - originalPrice: Double
  - discount: String
  - imageUrl: String
  - imageUrls: Array<String>
  - category: String
  - subCategory: String
  - productUrl: String
  - description: String
  - availability: Boolean
  - scrapedAt: Timestamp
  - updatedAt: Timestamp
```

## License

This project is for educational purposes only. Please respect Costco's terms of service and robots.txt.
