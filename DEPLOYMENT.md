# Ebee Core — VPS Deployment Guide

## Overview

Ebee Core runs on a VPS at `129.121.117.178` using Docker Compose at `/opt/ebee-core/`.

## Architecture

```
nginx (443 → reverse proxy + static files)
  └── app (Spring Boot, port 8085)
  └── db (PostgreSQL 16)
  └── redis (7)
  └── minio (S3-compatible object storage)
```

## Repositories

| Repo | URL | Purpose |
|------|-----|---------|
| Ebee_Core | https://github.com/tharshan2001/Ebee_Core | Backend (Spring Boot) + nginx config |
| bee-driver | https://github.com/tharshan2001/bee-driver | Driver mobile app (React Native/Expo) |
| Ebee_Admin | https://github.com/tharshan2001/Ebee_Admin | Admin web frontend |

## Environment Variables

See `.env.sample` in Ebee_Core. Required secrets:

- `DB_NAME` — PostgreSQL database name (`ebee_prod` or `ebee_test`)
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — PostgreSQL
- `JWT_SECRET`, `JWT_RESET_SECRET` — JWT auth (>= 256 bits)
- `REDIS_HOST`, `REDIS_PORT` — Redis connection
- `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET_NAME` — S3 storage
- `GMAIL_USERNAME`, `GMAIL_APP_PASSWORD` — Email notifications
- `APP_CORS_ALLOWED_ORIGINS` — Comma-separated CORS origins

## Database Management

Two separate PostgreSQL databases are used (both live on VPS):

| Database | Purpose | `DB_NAME` value | Status |
|----------|---------|-----------------|--------|
| `ebee_prod` | Production (live data) | `ebee_prod` | Standby |
| `ebee_test` | Testing (old `ebee_db` data) | `ebee_test` | **Active** — pointed as of 2026-08-06 |

The database name is driven by the `DB_NAME` env var in `.env`. Docker Compose uses it for `POSTGRES_DB`, the JDBC URL, and the healthcheck.

### ~~One-Time VPS Migration~~ (Completed — 2026-08-06)

> The migration below has already been run. `ebee_db` was renamed to `ebee_test` and a fresh `ebee_prod` was created. Flyway migrated `ebee_prod` automatically on startup.

<details>
<summary>Original migration steps (for reference)</summary>

```bash
ssh root@129.121.117.178
cd /opt/ebee-core

# Stop the app (keep db running)
docker compose stop app

# Rename current ebee_db → ebee_test
docker exec ebee-core-db-1 psql -U ebee -d postgres -c "ALTER DATABASE ebee_db RENAME TO ebee_test;"

# Create fresh production database
docker exec ebee-core-db-1 psql -U ebee -d postgres -c "CREATE DATABASE ebee_prod;"

# Update .env — set DB_NAME=ebee_prod and DB_URL to use it
sed -i 's/^DB_NAME=.*/DB_NAME=ebee_prod/' .env
sed -i 's|^DB_URL=.*|DB_URL=jdbc:postgresql://db:5432/${DB_NAME}|' .env

# Restart app (Flyway will run migrations on the empty ebee_prod)
docker compose up -d
```

</details>

### Switching to Test Database

```bash
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --force-recreate app
```

This overrides `DB_URL` and `POSTGRES_DB` to point at `ebee_test`.

### Switching Back to Production Database

```bash
docker compose up -d --force-recreate app
```

## Deployment

### 1. Backend + Nginx

```bash
# SSH to VPS
ssh root@129.121.117.178

# Pull latest code
cd /opt/ebee-core
git fetch origin
git pull origin main

# Rebuild backend image
docker build --pull -t ghcr.io/tharshan2001/ebee_core:latest .

# Restart app container
docker compose up -d --force-recreate app

# Reload nginx (if nginx config changed)
docker exec ebee-core-nginx-1 nginx -s reload
```

### 2. Admin Frontend

```bash
# On local machine
cd sites/Ebee
npm run build

# Copy to VPS
COPYFILE_DISABLE=1 tar -cf /tmp/ebee-admin-dist.tar -C dist .
scp /tmp/ebee-admin-dist.tar root@129.121.117.178:/tmp/

# On VPS — extract to frontend/dist (volume mount)
cd /opt/ebee-core/frontend
rm -rf dist && mkdir dist
cd dist && tar -xf /tmp/ebee-admin-dist.tar
```

Note: The `frontend/dist` directory is mounted into the nginx container as a volume. No container restart needed.

### 3. Driver App

```bash
# On local machine
cd sites/bee-driver
git fetch origin && git pull origin main
cd android && ./gradlew assembleDebug

# APK output: android/app/build/outputs/apk/debug/app-debug.apk
# Install on device: adb install android/app/build/outputs/apk/debug/app-debug.apk
```

### 4. Full Sync (all services)

```bash
# On VPS
cd /opt/ebee-core
git fetch origin && git pull origin main
docker build --pull -t ghcr.io/tharshan2001/ebee_core:latest .
docker compose up -d --force-recreate app
docker exec ebee-core-nginx-1 nginx -s reload
```

## Important Configuration

### Nginx — HTTP/2 and WebSocket

The `listen 443 ssl http2` directive **breaks WebSocket** on this nginx version. Use `listen 443 ssl` instead.

```nginx
# CORRECT (in nginx/nginx.https.conf)
listen 443 ssl;

# WRONG — breaks WebSocket
listen 443 ssl http2;
```

After modifying nginx config on VPS, reload with:
```bash
docker exec ebee-core-nginx-1 nginx -s reload
```

### Nginx — WebSocket Proxy

WebSocket endpoint at `/ws` must be proxied to the backend:

```nginx
location /ws {
    proxy_pass http://app:8085;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
}
```

### CORS

The backend WebSocket endpoint uses `setAllowedOriginPatterns` from `APP_CORS_ALLOWED_ORIGINS`. Ensure the env var includes all frontend origins:

```
APP_CORS_ALLOWED_ORIGINS=https://ebee.lk,https://www.ebee.lk
```

## STOMP/WebSocket Endpoints

| Endpoint | Type | Auth | Purpose |
|----------|------|------|---------|
| `/ws` | SockJS | JWT Bearer | Admin web STOMP connection |
| `/ws/websocket` | Raw WS | JWT Bearer | Driver app STOMP connection |
| `/app/driver-locations/update` | SEND | DRIVER | Driver sends location |
| `/app/driver-locations/snapshot` | SEND | ADMIN | Admin requests all driver locations |
| `/topic/driver-locations` | SUBSCRIBE | ADMIN/SUPER_ADMIN | Live location broadcast |
| `/user/queue/driver-locations-snapshot` | SUBSCRIBE | ADMIN/SUPER_ADMIN | Snapshot response |

## Driver Location Flow

1. Driver app connects via SockJS → `/ws/websocket` with JWT Bearer token
2. `WebSocketAuthInterceptor.preSend()` authenticates the STOMP CONNECT frame
3. Driver sends location to `/app/driver-locations/update` (throttled 5s via Redis SETNX)
4. `DriverLocationService.updateLocation()` stores in Redis (`driver:location:{id}`, 5min TTL) and broadcasts to `/topic/driver-locations`
5. Admin receives live update via `/topic/driver-locations` subscription

## Redis Keys

| Key Pattern | TTL | Purpose |
|-------------|-----|---------|
| `driver:location:{id}` | 5 min | Latest driver location |
| `driver:ratelimit:{id}` | 5 sec | Location update rate limiter |

## Common Issues

### Driver STOMP not connecting
- Use **SockJS** (`https://ebee.lk/ws`) not raw WebSocket
- Driver app: `@stomp/stompjs` v7 with `new SockJS(url)` factory
- Admin app: same SockJS approach

### Admin tracking page shows no drivers
- Check admin STOMP connection indicator (top bar — green = connected)
- Refresh the page if STOMP disconnected
- Check backend logs: `docker logs ebee-core-app-1 | grep Snapshot`
- Check Redis: `docker exec ebee-core-redis-1 redis-cli KEYS "driver:location:*"`

### Location stops after one update
- Driver app uses `getCurrentPositionAsync` polling (3s interval) instead of `watchPositionAsync`
- `watchPositionAsync` silently stops on some Android devices

### WebSocket 101 but no STOMP frames in backend
- Check nginx is not using `http2` on port 443 (breaks WebSocket upgrade)
- Check `proxy_http_version 1.1`, `Upgrade`, and `Connection` headers are set

## Useful Commands

```bash
# Check which database is active
docker exec ebee-core-app-1 printenv DB_URL

# Backend logs
docker logs -f ebee-core-app-1 | grep -E "Broadcast|Snapshot|ERROR"

# List databases
docker exec ebee-core-db-1 psql -U ebee -d postgres -c "\l" | grep ebee

# Redis driver locations
docker exec ebee-core-redis-1 redis-cli KEYS "driver:location:*"
docker exec ebee-core-redis-1 redis-cli GET "driver:location:<id>"

# Nginx logs
docker logs ebee-core-nginx-1 --tail 50

# Restart specific service
docker compose restart app
docker compose restart nginx

# Full redeploy
docker compose down && docker compose up -d
```
