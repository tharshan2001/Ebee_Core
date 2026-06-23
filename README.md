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

## API Documentation

See `docs/` for per-module API documentation.

## License

Proprietary — Ebee.
