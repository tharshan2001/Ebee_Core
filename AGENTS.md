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

## Current State (July 9, 2026)

### FIFO-Layered WAC Implemented
- New `inventory_layers` table (V79 migration) stores per-receipt cost layers: `quantity`, `remaining_quantity`, `unit_cost`, `received_at`
- `InventoryLayer` entity + `InventoryLayerRepository` with queries for FIFO consumption ordering by `received_at ASC`
- `recordPurchaseStocks()` inserts a new layer with the received quantity and unit cost, recalculates WAC from all remaining layers
- `recordOrderStock(s)` with OUT consumes from oldest layers first (FIFO), recalcs WAC
- `recordOrderStock(s)` with RETURN adds a new layer at current WAC
- `confirmOrderStock()` also consumes FIFO layers
- Private helpers: `consumeFifoLayers()`, `recalculateWac()`
- The `stock` table remains a quantity-only audit log (no cost stored)
- `order_items.cost_price` snapshots the WAC at order time (before FIFO consumption), giving a reasonable COGS approximation

### Curl-Verified
- Two receipts at different costs (100, 200) → two layers with correct remaining qty
- WAC correctly calculated from layers: (10×100 + 10×200)/20 = 150
- FIFO consumption: selling 5 consumes from oldest layer (10→5 remaining), WAC recalculates to 166.67
- Further sale of 6 exhausts layer 1 (5→0) and consumes 1 from layer 2 (10→9), WAC = 200
- 308 tests pass

### Combo (Product Bundle) Feature
- New `combo/` domain package (entity, dto, enums, exception, mapper, repository, service, controller)
- Three rule types: null (plain bundle), `FREE_PRODUCT` (admin picks exact free product), `DISCOUNT` (FIXED/PERCENTAGE)
- Combo price auto-calculated as `sum(product.price * quantity)` — no manual pricing
- Combos immutable after first order: edits clone new combo (new UUID) and deactivate old; delete blocked with `ComboImmutabilityException`
- `InventoryService.checkStockAvailability()` — read-only batch stock validation before order creation
- `recordOrderStocks()` called exactly ONCE per order (validates + deducts + FIFO)
- `order_items.combo_id` FK + `combo_items` / `combo_rules` tables (V89/V90)
- `ComboImmutabilityException` mapped to 400 in `GlobalExceptionHandler`
- `/api/combos/**` public for GET (like products/categories) in SecurityConfig + JwtAuthenticationFilter
- 351 tests pass (2 pre-existing CommissionLockTest errors need running DB)

## Agent Workflow

1. Before editing, run `./mvnw clean compile` to verify the baseline.
2. After changes, run `./mvnw test`.
3. Run `./mvnw verify` before committing.
4. If adding a new domain, mirror the existing `order/` or `product/` package layout.
5. Ask the user before running `git commit`, `git push`, or destructive database operations.
