#!/bin/bash
set -e

# Production deploy script for Ebee Core
# Usage: ./scripts/deploy.sh [tag]
#   tag: image tag to deploy (default: latest)

TAG=${1:-latest}
REPO="ghcr.io/tharshan2001/ebee_core"

echo "=== Ebee Core Deploy ==="
echo "Image: $REPO:$TAG"

# Pull new image
docker pull "$REPO:$TAG"

# Tag as latest if a specific tag was given
if [ "$TAG" != "latest" ]; then
  docker tag "$REPO:$TAG" "$REPO:latest"
fi

# Recreate app container
docker compose up -d --force-recreate --no-deps app

# Clean up old images
docker image prune -f

echo "=== Deploy complete ==="
