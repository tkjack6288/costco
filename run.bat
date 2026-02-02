@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ==============================================
REM Costco Scraper - Run Script
REM ==============================================

REM Load environment variables from .env if exists
if exist ".env" (
    for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
        set "line=%%a"
        if not "!line:~0,1!"=="#" (
            set "%%a=%%b"
        )
    )
    echo Loaded environment from .env
)

REM Default values
if not defined SPRING_PROFILES_ACTIVE set SPRING_PROFILES_ACTIVE=local
if not defined PORT set PORT=8080

echo ==========================================
echo   Starting Costco Scraper
echo   Profile: %SPRING_PROFILES_ACTIVE%
echo   Port: %PORT%
echo ==========================================

REM Check if credentials file exists
if defined GOOGLE_APPLICATION_CREDENTIALS (
    if not exist "%GOOGLE_APPLICATION_CREDENTIALS%" (
        echo Warning: GOOGLE_APPLICATION_CREDENTIALS file not found: %GOOGLE_APPLICATION_CREDENTIALS%
        echo Firestore operations may fail without proper credentials.
    )
)

REM Run with Maven
call mvn spring-boot:run ^
    -Dspring-boot.run.profiles=%SPRING_PROFILES_ACTIVE% ^
    -Dspring-boot.run.jvmArguments="-Dserver.port=%PORT%"

endlocal
