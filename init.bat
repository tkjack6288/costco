@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ==============================================
REM Costco Scraper - Project Initialization Script
REM ==============================================

echo ==========================================
echo   Costco Scraper - Initialization
echo ==========================================

REM Check prerequisites
echo.
echo [1/6] Checking prerequisites...

REM Check Java
java -version >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK] Java found
) else (
    echo [ERROR] Java not found. Please install Java 17+
    exit /b 1
)

REM Check Maven
mvn -version >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK] Maven found
) else (
    echo [ERROR] Maven not found. Please install Maven 3.8+
    exit /b 1
)

REM Check Docker (optional)
docker --version >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK] Docker found
) else (
    echo [WARN] Docker not found (optional for local development)
)

REM Check gcloud (optional)
gcloud --version >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK] Google Cloud SDK found
) else (
    echo [WARN] gcloud not found (required for deployment)
)

REM Create environment file
echo.
echo [2/6] Creating environment file...

if exist ".env" (
    echo [SKIP] .env file already exists
) else (
    (
        echo # Google Cloud Configuration
        echo GCP_PROJECT_ID=your-project-id
        echo GOOGLE_APPLICATION_CREDENTIALS=./credentials.json
        echo.
        echo # Application Configuration
        echo PORT=8080
        echo SPRING_PROFILES_ACTIVE=local
        echo.
        echo # Scraper Configuration
        echo SCRAPER_DELAY_MIN_MS=3000
        echo SCRAPER_DELAY_MAX_MS=5000
        echo SCRAPER_MAX_PAGES=100
        echo.
        echo # Selenium Configuration
        echo SELENIUM_HEADLESS=true
    ) > .env
    echo [OK] Created .env file
    echo      Please edit .env with your configuration
)

REM Create local application properties
echo.
echo [3/6] Creating local configuration...

set LOCAL_CONFIG=src\main\resources\application-local.yml

if exist "%LOCAL_CONFIG%" (
    echo [SKIP] %LOCAL_CONFIG% already exists
) else (
    (
        echo # Local Development Configuration
        echo spring:
        echo   cloud:
        echo     gcp:
        echo       firestore:
        echo         project-id: ${GCP_PROJECT_ID:your-project-id}
        echo.
        echo # Override scraper settings for local testing
        echo scraper:
        echo   delay:
        echo     min-ms: 1000
        echo     max-ms: 2000
        echo   page:
        echo     max-pages: 5
        echo.
        echo # More verbose logging for development
        echo logging:
        echo   level:
        echo     com.costco.scraper: DEBUG
        echo     org.springframework.web: DEBUG
    ) > %LOCAL_CONFIG%
    echo [OK] Created %LOCAL_CONFIG%
)

REM Download dependencies
echo.
echo [4/6] Downloading Maven dependencies...
call mvn dependency:go-offline -B
if %errorlevel% equ 0 (
    echo [OK] Dependencies downloaded
) else (
    echo [ERROR] Failed to download dependencies
    exit /b 1
)

REM Build project
echo.
echo [5/6] Building project...
call mvn clean compile -B
if %errorlevel% equ 0 (
    echo [OK] Project compiled successfully
) else (
    echo [ERROR] Build failed
    exit /b 1
)

REM Create directories
echo.
echo [6/6] Creating directories...

if not exist "logs" mkdir logs
if not exist "data" mkdir data
if not exist "temp" mkdir temp

echo [OK] Created directories: logs, data, temp

REM Print next steps
echo.
echo ==========================================
echo   Initialization Complete!
echo ==========================================
echo.
echo Next Steps:
echo.
echo 1. Configure Google Cloud credentials:
echo    - Create a service account in GCP Console
echo    - Download the JSON key file
echo    - Save as 'credentials.json' in project root
echo    - Or set GOOGLE_APPLICATION_CREDENTIALS env var
echo.
echo 2. Edit .env file with your GCP_PROJECT_ID
echo.
echo 3. Enable Firestore in your GCP project:
echo    gcloud firestore databases create --location=asia-east1
echo.
echo 4. Run the application:
echo    run.bat               (Using the run script)
echo    mvn spring-boot:run   (Using Maven directly)
echo.
echo 5. Test the API:
echo    curl http://localhost:8080/api/scraper/health
echo    curl -X POST http://localhost:8080/api/scraper/trigger
echo.
echo 6. For Docker deployment:
echo    docker build -t costco-scraper .
echo    docker run -p 8080:8080 costco-scraper
echo.

endlocal
