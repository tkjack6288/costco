#!/bin/bash

# ==============================================
# Costco Scraper - Run Script
# ==============================================

set -e

# Load environment variables from .env if exists
if [ -f ".env" ]; then
    export $(cat .env | grep -v '^#' | xargs)
    echo "Loaded environment from .env"
fi

# Default values
PROFILE=${SPRING_PROFILES_ACTIVE:-local}
PORT=${PORT:-8080}

echo "=========================================="
echo "  Starting Costco Scraper"
echo "  Profile: $PROFILE"
echo "  Port: $PORT"
echo "=========================================="

# Check if credentials file exists
if [ -n "$GOOGLE_APPLICATION_CREDENTIALS" ] && [ ! -f "$GOOGLE_APPLICATION_CREDENTIALS" ]; then
    echo "Warning: GOOGLE_APPLICATION_CREDENTIALS file not found: $GOOGLE_APPLICATION_CREDENTIALS"
    echo "Firestore operations may fail without proper credentials."
fi

# Run with Maven
mvn spring-boot:run \
    -Dspring-boot.run.profiles=$PROFILE \
    -Dspring-boot.run.jvmArguments="-Dserver.port=$PORT"
