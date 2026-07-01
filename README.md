# Ebee Core

Spring Boot backend for Ebee — a Sri Lankan POS, commission, inventory, and order-management system.

## Tech Stack

- Java 21
- Spring Boot 3.4.x
- Maven
- PostgreSQL (schema managed by Flyway)
- Redis
- Spring Security + JWT
- AWS S3
- Spring Mail + Dialog SMS

## Quick Start

1. Copy environment template:
   ```bash
   cp .env.sample .env
   ```
2. Fill in real values in `.env` (database, JWT secrets, AWS, Gmail, etc.).
3. Start PostgreSQL and Redis locally (or adjust `.env` to point to your services).
4. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

## Build & Test

```bash
./mvnw clean compile
./mvnw test
./mvnw verify
```

## Project Structure

```
src/main/java/com/ebee/
├── <domain>/           # order, product, agent, customer, etc.
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/
│   ├── mapper/
│   └── enums/
├── auth/               # JWT, security filters, config
├── common/             # shared utilities
└── notification/       # email & SMS senders
```

## Architecture Rules

- Controllers depend on services only.
- No new Lombok usage.
- Services should be small and single-responsibility.
- No bidirectional package cycles.
- Secrets are loaded from `.env` and never committed.

See `AGENTS.md` for the full agent/coding conventions.

## Production Deployment (Docker)

### Prerequisites

- Docker 24+ with Compose plugin on a Linux VPS
- A domain name pointing to your VPS IP
- Ports 80 and 443 open in the firewall

### Quick Start

```bash
# Clone onto the VPS
git clone https://github.com/tharshan2001/Ebee_Core.git /opt/ebee
cd /opt/ebee

# Configure secrets
cp .env.sample .env
vim .env

# Bootstrap SSL (one time)
mkdir -p nginx/certbot/{conf,www}
docker compose up -d nginx          # starts HTTP-only
docker compose run --rm certbot certonly --webroot \
  -w /var/www/certbot -d ebee.lk   # get Let's Encrypt cert
docker compose up -d                # full stack with HTTPS

# Auto-renew SSL (add to crontab)
echo "0 3 * * * cd /opt/ebee && docker compose run --rm certbot renew \
  && docker compose exec nginx nginx -s reload" | crontab -
```

### Services

| Service | Port | Notes |
|---|---|---|
| `nginx` | 80 / 443 | Reverse proxy with HTTPS + WebSocket |
| `app` | 8085 | Spring Boot (internal) |
| `db` | 5432 | PostgreSQL 16 (internal, persistent volume) |
| `redis` | 6379 | Redis 7 (internal) |

### Management

```bash
# View logs
docker compose logs -f app
docker compose logs -f nginx

# Restart a service
docker compose restart app

# Update after pulling new code
git pull
docker compose build app
docker compose up -d

# Backup database
./scripts/db.sh backup

# Health check
curl https://ebee.lk/actuator/health
```

### Environment Variables

All configuration is driven by `.env`. See `.env.sample` for the full list.

## API Documentation

See `docs/` for per-module API documentation.

## License

Proprietary — Ebee.
