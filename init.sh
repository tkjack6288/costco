#!/bin/bash

# ==============================================
# Costco Scraper - Project Initialization Script
# ==============================================

set -e

echo "=========================================="
echo "  Costco Scraper - Initialization"
echo "=========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check prerequisites
check_prerequisites() {
    echo -e "\n${YELLOW}[1/6] Checking prerequisites...${NC}"

    # Check Java
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
        echo -e "${GREEN}✓ Java found: $JAVA_VERSION${NC}"
    else
        echo -e "${RED}✗ Java not found. Please install Java 17+${NC}"
        exit 1
    fi

    # Check Maven
    if command -v mvn &> /dev/null; then
        MVN_VERSION=$(mvn -version 2>&1 | head -n 1)
        echo -e "${GREEN}✓ Maven found: $MVN_VERSION${NC}"
    else
        echo -e "${RED}✗ Maven not found. Please install Maven 3.8+${NC}"
        exit 1
    fi

    # Check Docker (optional)
    if command -v docker &> /dev/null; then
        DOCKER_VERSION=$(docker --version)
        echo -e "${GREEN}✓ Docker found: $DOCKER_VERSION${NC}"
    else
        echo -e "${YELLOW}! Docker not found (optional for local development)${NC}"
    fi

    # Check gcloud (optional)
    if command -v gcloud &> /dev/null; then
        GCLOUD_VERSION=$(gcloud --version 2>&1 | head -n 1)
        echo -e "${GREEN}✓ Google Cloud SDK found: $GCLOUD_VERSION${NC}"
    else
        echo -e "${YELLOW}! gcloud not found (required for deployment)${NC}"
    fi
}

# Create environment file
create_env_file() {
    echo -e "\n${YELLOW}[2/6] Creating environment file...${NC}"

    if [ -f ".env" ]; then
        echo -e "${YELLOW}! .env file already exists, skipping...${NC}"
    else
        cat > .env << 'EOF'
# Google Cloud Configuration
GCP_PROJECT_ID=your-project-id
GOOGLE_APPLICATION_CREDENTIALS=./credentials.json

# Application Configuration
PORT=8080
SPRING_PROFILES_ACTIVE=local

# Scraper Configuration
SCRAPER_DELAY_MIN_MS=3000
SCRAPER_DELAY_MAX_MS=5000
SCRAPER_MAX_PAGES=100

# Selenium Configuration
SELENIUM_HEADLESS=true
EOF
        echo -e "${GREEN}✓ Created .env file${NC}"
        echo -e "${YELLOW}  Please edit .env with your configuration${NC}"
    fi
}

# Create local application properties
create_local_config() {
    echo -e "\n${YELLOW}[3/6] Creating local configuration...${NC}"

    LOCAL_CONFIG="src/main/resources/application-local.yml"

    if [ -f "$LOCAL_CONFIG" ]; then
        echo -e "${YELLOW}! $LOCAL_CONFIG already exists, skipping...${NC}"
    else
        cat > "$LOCAL_CONFIG" << 'EOF'
# Local Development Configuration
spring:
  cloud:
    gcp:
      firestore:
        project-id: ${GCP_PROJECT_ID:your-project-id}

# Override scraper settings for local testing
scraper:
  delay:
    min-ms: 1000
    max-ms: 2000
  page:
    max-pages: 5

# More verbose logging for development
logging:
  level:
    com.costco.scraper: DEBUG
    org.springframework.web: DEBUG
EOF
        echo -e "${GREEN}✓ Created $LOCAL_CONFIG${NC}"
    fi
}

# Download dependencies
download_dependencies() {
    echo -e "\n${YELLOW}[4/6] Downloading Maven dependencies...${NC}"
    mvn dependency:go-offline -B
    echo -e "${GREEN}✓ Dependencies downloaded${NC}"
}

# Build project
build_project() {
    echo -e "\n${YELLOW}[5/6] Building project...${NC}"
    mvn clean compile -B
    echo -e "${GREEN}✓ Project compiled successfully${NC}"
}

# Create directories
create_directories() {
    echo -e "\n${YELLOW}[6/6] Creating directories...${NC}"

    mkdir -p logs
    mkdir -p data
    mkdir -p temp

    echo -e "${GREEN}✓ Created directories: logs, data, temp${NC}"
}

# Print next steps
print_next_steps() {
    echo -e "\n=========================================="
    echo -e "${GREEN}  Initialization Complete!${NC}"
    echo -e "==========================================\n"

    echo -e "${YELLOW}Next Steps:${NC}"
    echo ""
    echo "1. Configure Google Cloud credentials:"
    echo "   - Create a service account in GCP Console"
    echo "   - Download the JSON key file"
    echo "   - Save as 'credentials.json' in project root"
    echo "   - Or set GOOGLE_APPLICATION_CREDENTIALS env var"
    echo ""
    echo "2. Edit .env file with your GCP_PROJECT_ID"
    echo ""
    echo "3. Enable Firestore in your GCP project:"
    echo "   gcloud firestore databases create --location=asia-east1"
    echo ""
    echo "4. Run the application:"
    echo "   ./run.sh            # Using the run script"
    echo "   mvn spring-boot:run # Using Maven directly"
    echo ""
    echo "5. Test the API:"
    echo "   curl http://localhost:8080/api/scraper/health"
    echo "   curl -X POST http://localhost:8080/api/scraper/trigger"
    echo ""
    echo "6. For Docker deployment:"
    echo "   docker build -t costco-scraper ."
    echo "   docker run -p 8080:8080 costco-scraper"
    echo ""
}

# Main execution
main() {
    check_prerequisites
    create_env_file
    create_local_config
    download_dependencies
    build_project
    create_directories
    print_next_steps
}

main "$@"
