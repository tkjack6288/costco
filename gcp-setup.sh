#!/bin/bash

# ==============================================
# Google Cloud Platform Setup Script
# ==============================================

set -e

echo "=========================================="
echo "  GCP Setup for Costco Scraper"
echo "=========================================="

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Check gcloud
if ! command -v gcloud &> /dev/null; then
    echo -e "${RED}Error: gcloud CLI not found. Please install Google Cloud SDK${NC}"
    echo "Visit: https://cloud.google.com/sdk/docs/install"
    exit 1
fi

# Get project ID
if [ -z "$GCP_PROJECT_ID" ]; then
    echo -e "\n${YELLOW}Enter your GCP Project ID:${NC}"
    read -r GCP_PROJECT_ID
fi

if [ -z "$GCP_PROJECT_ID" ]; then
    echo -e "${RED}Error: GCP_PROJECT_ID is required${NC}"
    exit 1
fi

echo -e "\n${GREEN}Using Project: $GCP_PROJECT_ID${NC}"

# Set project
echo -e "\n${YELLOW}[1/6] Setting GCP project...${NC}"
gcloud config set project "$GCP_PROJECT_ID"

# Enable APIs
echo -e "\n${YELLOW}[2/6] Enabling required APIs...${NC}"
gcloud services enable firestore.googleapis.com
gcloud services enable run.googleapis.com
gcloud services enable cloudbuild.googleapis.com
gcloud services enable cloudscheduler.googleapis.com
gcloud services enable containerregistry.googleapis.com
echo -e "${GREEN}✓ APIs enabled${NC}"

# Create Firestore database
echo -e "\n${YELLOW}[3/6] Creating Firestore database...${NC}"
if gcloud firestore databases describe --project="$GCP_PROJECT_ID" &> /dev/null; then
    echo -e "${YELLOW}! Firestore database already exists${NC}"
else
    gcloud firestore databases create --location=asia-east1 --project="$GCP_PROJECT_ID"
    echo -e "${GREEN}✓ Firestore database created${NC}"
fi

# Create service account
echo -e "\n${YELLOW}[4/6] Creating service account...${NC}"
SA_NAME="costco-scraper-sa"
SA_EMAIL="${SA_NAME}@${GCP_PROJECT_ID}.iam.gserviceaccount.com"

if gcloud iam service-accounts describe "$SA_EMAIL" &> /dev/null; then
    echo -e "${YELLOW}! Service account already exists${NC}"
else
    gcloud iam service-accounts create "$SA_NAME" \
        --display-name="Costco Scraper Service Account"
    echo -e "${GREEN}✓ Service account created${NC}"
fi

# Grant permissions
echo -e "\n${YELLOW}[5/6] Granting permissions...${NC}"
gcloud projects add-iam-policy-binding "$GCP_PROJECT_ID" \
    --member="serviceAccount:$SA_EMAIL" \
    --role="roles/datastore.user" \
    --quiet

gcloud projects add-iam-policy-binding "$GCP_PROJECT_ID" \
    --member="serviceAccount:$SA_EMAIL" \
    --role="roles/logging.logWriter" \
    --quiet

echo -e "${GREEN}✓ Permissions granted${NC}"

# Create key file
echo -e "\n${YELLOW}[6/6] Creating credentials file...${NC}"
if [ -f "credentials.json" ]; then
    echo -e "${YELLOW}! credentials.json already exists. Skipping...${NC}"
    echo "  Delete the file and re-run if you need a new key."
else
    gcloud iam service-accounts keys create credentials.json \
        --iam-account="$SA_EMAIL"
    echo -e "${GREEN}✓ Created credentials.json${NC}"
fi

# Update .env file
echo -e "\n${YELLOW}Updating .env file...${NC}"
if [ -f ".env" ]; then
    # Update GCP_PROJECT_ID in .env
    if grep -q "GCP_PROJECT_ID=" .env; then
        sed -i.bak "s/GCP_PROJECT_ID=.*/GCP_PROJECT_ID=$GCP_PROJECT_ID/" .env
        rm -f .env.bak
    fi
fi
echo -e "${GREEN}✓ .env updated${NC}"

# Print summary
echo -e "\n=========================================="
echo -e "${GREEN}  GCP Setup Complete!${NC}"
echo -e "==========================================\n"

echo "Project ID:     $GCP_PROJECT_ID"
echo "Service Account: $SA_EMAIL"
echo "Credentials:    ./credentials.json"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo ""
echo "1. For local development:"
echo "   export GOOGLE_APPLICATION_CREDENTIALS=./credentials.json"
echo "   ./run.sh"
echo ""
echo "2. For Cloud Run deployment:"
echo "   - Add GCP_PROJECT_ID to GitHub Secrets"
echo "   - Add contents of credentials.json as GCP_SA_KEY secret"
echo "   - Push to main branch"
echo ""
echo "3. To create Cloud Scheduler job (after deployment):"
echo "   gcloud scheduler jobs create http costco-scraper-daily \\"
echo "     --location=asia-east1 \\"
echo "     --schedule='0 3 * * *' \\"
echo "     --uri='YOUR_CLOUD_RUN_URL/api/scraper/trigger' \\"
echo "     --http-method=POST \\"
echo "     --time-zone='Asia/Taipei'"
echo ""
