# Deployment Guide

## VPS Information
- **IP**: (Ask user)
- **SSH**: `ssh root@<VPS_IP>` (password: (Ask user))
- **Location**: `/opt/ebee-core` (backend), `/opt/Ebee` (frontend)

## Services
| Service | Container | Port |
|---------|-----------|------|
| app | ebee-core-app | 8085 |
| nginx | ebee-core-nginx | 80, 443 |
| db | ebee-core-db | 5432 |
| redis | ebee-core-redis | 6379 |
| minio | ebee-core-minio | 9000, 9001 |

## Deployment Commands

### Full Deployment (Backend + Frontend)
```bash
# 1. Backend - Pull, Build & Restart
cd /opt/ebee-core && git pull origin main && docker compose build app && docker compose up -d app

# 2. Frontend - Pull, Build & Deploy
cd /opt/Ebee && git stash && git pull origin main && npm install && npx vite build

# 3. Update .env with production URLs
echo 'VITE_API_URL=https://ebee.lk/api
VITE_WS_URL=https://ebee.lk/ws' > /opt/Ebee/.env

# 4. Deploy Frontend to Nginx
rm -rf /opt/ebee-core/frontend/dist && cp -r /opt/Ebee/dist /opt/ebee-core/frontend/

# 5. Restart Nginx
cd /opt/ebee-core && docker compose stop nginx && docker compose rm -f nginx && docker compose up -d
```

### Backend Only
```bash
cd /opt/ebee-core
git pull origin main
docker compose build app
docker compose up -d app
```

### Frontend Only
```bash
cd /opt/Ebee
git stash
git pull origin main
npm install
npx vite build
echo 'VITE_API_URL=https://ebee.lk/api
VITE_WS_URL=https://ebee.lk/ws' > /opt/Ebee/.env
rm -rf /opt/ebee-core/frontend/dist && cp -r /opt/Ebee/dist /opt/ebee-core/frontend/
cd /opt/ebee-core && docker compose stop nginx && docker compose rm -f nginx && docker compose up -d
```

### Restart All Services
```bash
cd /opt/ebee-core
docker compose restart
```

### Check Service Status
```bash
cd /opt/ebee-core
docker compose ps
curl -s -o /dev/null -w '%{http_code}' https://ebee.lk/
```

## Environment Variables

### Backend (.env at /opt/ebee-core/.env)
```
DRIVER_GPS_INTERVAL_MS=1000
DRIVER_GPS_MIN_ACCURACY=10
```
Note: Other secrets are stored in the existing .env file.

### Frontend (.env at /opt/Ebee/.env)
```
VITE_API_URL=https://ebee.lk/api
VITE_WS_URL=https://ebee.lk/ws
```

## Troubleshooting

### App Not Starting
```bash
docker compose logs app --tail 50
```

### Nginx Empty/403 Error
```bash
docker exec ebee-core-nginx-1 ls -la /usr/share/nginx/html/
# If empty:
docker compose stop nginx && docker compose rm -f nginx && docker compose up -d
```

### Build Fails (Test Compilation)
If Maven build fails on test compilation, modify Dockerfile:
```bash
sed -i 's/-DskipTests/-Dmaven.test.skip=true/' /opt/ebee-core/Dockerfile
docker compose build app
```

### Missing NPM Packages
```bash
cd /opt/Ebee && npm install <package-name>
```

## Health Checks
```bash
# Check all containers
docker compose ps

# Check app logs
docker compose logs app --tail 30

# Check nginx logs
docker compose logs nginx --tail 30

# Test API
curl -s -o /dev/null -w '%{http_code}' https://ebee.lk/api/profile
```

## Git Branches
- **Backend**: `main` at `/opt/ebee-core`
- **Frontend**: `main` at `/opt/Ebee`
