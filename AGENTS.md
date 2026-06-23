# Ebee Core — Agent Instructions

## Project Overview

Ebee Core is a Sri Lankan POS, commission, inventory, and order-management monolith built with Spring Boot.

- **Language**: Java 21
- **Framework**: Spring Boot 3.4.x (stable)
- **Build tool**: Maven (`./mvnw`)
- **Database**: PostgreSQL (schema managed by Flyway)
- **Cache**: Redis
- **Security**: Spring Security + JWT
- **Object storage**: AWS S3
- **Notifications**: Spring Mail (Gmail SMTP) + Dialog SMS

## Project Structure

```
src/main/java/com/ebee/
├── EbeeApplication.java
├── <domain>/                  # e.g. order, product, agent, customer
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/
│   ├── mapper/
│   ├── enums/
│   └── config/                # domain-specific config
├── auth/                      # JWT, security filters, config
├── common/                    # shared utilities, filters
└── notification/
```

## Coding Standards

1. **No new Lombok usage**. Existing Lombok is grandfathered in; do not add new Lombok annotations. Prefer records for DTOs and explicit constructors/getters/setters for entities.
2. **Controllers must NOT call repositories directly**. Controllers depend on services only.
3. **No god services**. Services should have a single responsibility. If a service grows beyond ~250 lines, split it.
4. **No package cycles**. Entities must not create bidirectional import cycles.
5. **Use `@DataJpaTest` for repository tests**, Mockito for service unit tests, and `@SpringBootTest` + Testcontainers for integration tests.
6. **Configuration**: prefer `@ConfigurationProperties` with constructor binding over field-injected `@Value`.
7. **Secrets**: all secrets come from environment variables loaded via `.env`. **Never commit `.env`**. Only `.env.sample` may be read or displayed.
8. **Database**: Flyway migrations live in `src/main/resources/db/migration/`. Use `ddl-auto=validate`.

## Forbidden Patterns

- New Lombok annotations
- Controllers injecting `*Repository`
- `@Data` on JPA entities
- `@EqualsAndHashCode` on entities over collections
- Field injection of `@Value`
- Business logic in controllers

## Build & Test Commands

```bash
# Build
./mvnw clean compile

# Unit tests
./mvnw test

# Integration tests
./mvnw verify

# Run locally (needs .env)
./mvnw spring-boot:run
```

## Required Environment Variables

See `.env.sample`. Critical secrets that must be present at runtime:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET` (>= 256 bits)
- `JWT_RESET_SECRET` (different from JWT_SECRET)
- `SUPER_ADMIN_PASSWORD`
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_BUCKET_NAME`
- `GMAIL_USERNAME`, `GMAIL_APP_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`

## Security Notes

- Role hierarchy: `ROLE_SUPER_ADMIN > ROLE_ADMIN > ROLE_AGENT`
- Public endpoints are configured in `SecurityConfig.java`
- CORS origins are configured via `app.cors.allowed-origins`
- IP whitelisting reads `X-Forwarded-For`/`X-Real-IP`; ensure the app runs behind a trusted reverse proxy

## Agent Workflow

1. Before editing, run `./mvnw clean compile` to verify the baseline.
2. After changes, run `./mvnw test`.
3. Run `./mvnw verify` before committing.
4. If adding a new domain, mirror the existing `order/` or `product/` package layout.
5. Ask the user before running `git commit`, `git push`, or destructive database operations.
