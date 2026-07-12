#!/bin/bash
set -e

# deploy.sh — Pull latest code, build frontend, and restart services
# Run this on the server from /opt/ebee-core (or wherever docker-compose.yml lives)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "==> Pulling latest backend code..."
git pull origin main

echo "==> Pulling latest frontend code..."
if [ -d "../Ebee" ]; then
    cd ../Ebee
    git pull origin main
    echo "==> Building frontend..."
    npm ci --silent
    npm run build
    echo "==> Copying frontend dist to backend..."
    rm -rf "$SCRIPT_DIR/frontend/dist"
    mkdir -p "$SCRIPT_DIR/frontend"
    cp -r dist "$SCRIPT_DIR/frontend/dist"
    cd "$SCRIPT_DIR"
elif [ -d "./frontend-repo" ]; then
    cd ./frontend-repo
    git pull origin main
    echo "==> Building frontend..."
    npm ci --silent
    npm run build
    echo "==> Copying frontend dist..."
    rm -rf "$SCRIPT_DIR/frontend/dist"
    mkdir -p "$SCRIPT_DIR/frontend"
    cp -r dist "$SCRIPT_DIR/frontend/dist"
    cd "$SCRIPT_DIR"
else
    echo "WARN: Frontend repo not found. Skipping frontend build."
    echo "      Place built frontend at ./frontend/dist manually."
fi

echo "==> Pulling latest Docker images..."
docker compose pull app nginx

echo "==> Restarting services..."
docker compose up -d --force-recreate --no-deps app nginx

echo "==> Pruning old images..."
docker image prune -f

echo "==> Deploy complete!"
docker compose ps
