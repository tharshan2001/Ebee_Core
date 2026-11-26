# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Ebee Core: the Spring Boot backend (Java 21, Spring Boot 3.5, Maven, PostgreSQL + Flyway, Redis, JWT) for Ebee.lk, a Sri Lankan order, delivery, inventory, commission and accounting system.

- **Clients:** the admin dashboard (`../Ebee`, GitHub `tharshan2001/Ebee_Admin`), the agent portal (`../bee-portal`) and the driver app (`../bee-driver`). Changing a response shape or role rule here can break them.
- **`AGENTS.md`:** holds the project rules plus a dated "Current State" log of past work. Read the rules; treat the log as history, not instructions.

## Commands

```bash
./mvnw spring-boot:run          # needs .env (copied from .env.sample, loaded by spring-dotenv); serves on :8085
./mvnw test                     # full suite: unit + Testcontainers integration tests (Docker must be running)
./mvnw test -Dtest=PayoutBatchTest                    # one class
./mvnw test -Dtest='PayoutBatchTest#onlyCommissions*' # one method (wildcards ok)
./mvnw test -Dtest='Expense*Test'                     # a group
./mvnw verify                   # what CI runs
```

- **Quiet runs:** `-q` hides passes. Check results with `grep "Tests run" target/surefire-reports/*.txt` or run without `-q`.
- **Local app:** the developer usually runs the app from IntelliJ (`.run/EbeeApp.run.xml`) against a local Postgres `ebee_db`. Don't kill or restart that process yourself; ask. A restart is needed for new endpoints and migrations to take effect.
- **Secrets:** never commit or print `.env`, `.firebase-service-account.json`, or anything under `backups/`.

## Architecture

**Package layout:** package-by-domain, `com.ebee.<domain>.{controller,service,repository,entity,dto,mapper,enums,event}`, with shared code in `common/` and security in `auth/`.

**Responses and errors**
- Controllers return `ResponseEntity<ApiResponse<T>>`, built with `ApiResponse.success(message, data)`. Clients unwrap `.data`.
- Lists are usually Spring `Page` with `size` capped at 200 in the service.
- Business rules signal failure with exceptions. `GlobalExceptionHandler` maps them:
  - `IllegalArgumentException` → 400
  - `IllegalStateException` → 409 (429 if the message contains "Too many")
  - `ResourceNotFoundException` → 404
  - `AccessDeniedException` → 403
  - `PeriodClosedException` → its own handler

  The exception message is shown to the user as-is, so write it for them.

**Security**
- `auth/security/SecurityConfig.java` permits a few public paths and requires authentication for everything else. Per-endpoint authorization is `@PreAuthorize` on controller methods.
- A controller without it is open to any signed-in user, including agents, drivers and customers. Every new endpoint needs an explicit `@PreAuthorize`.
- Role hierarchy: `SUPER_ADMIN > ADMIN > AGENT`, so `hasRole('ADMIN')` admits super admins.
- When a non-admin may see only their own records, check ownership in the service. Pattern: `securityService.isAdmin()`, then `agentRepository.findByUserId(securityService.getCurrentUserId())`.
- JWT is read only from the `Authorization` header.

**Money and accounting** (the most interconnected part; read `accounting/service/LedgerService.java` first)
- **No double-entry general ledger.** Money is tracked in:
  - three party ledgers: `customer_ledger`, `supplier_ledger`, `agent_ledger`, each with its own sign convention;
  - `finance/` money accounts (bank and cash) via `MoneyAccountService.recordMovement`, with a default account per purpose: EXPENSE, COMMISSION_PAYOUT, REFUND, payment methods.
- **Ledger posting:** every post goes through `LedgerService`, which:
  1. calls `AccountingPeriodService.requireOpenCurrentPeriod()`, so a CLOSED current month rejects the posting;
  2. writes the ledger row;
  3. records an `accounting_period_transactions` row tagged with a reference type (`ORDER`, `PAYMENT`, `SUPPLIER_INVOICE`, `AGENT_PAYOUT`, …).

  New money flows must go through these services, not straight to repositories.
- **Stored balances are derived:** `customer.outstandingBalance` / `advanceBalance` are synced from the ledgers (`syncCustomerOutstanding`, `syncAdvanceBalance`). Don't do arithmetic on them directly.
- **Inventory cost:** FIFO cost layers (`inventory_layers`) with a weighted average cost. `order_items.cost_price` snapshots cost at order time.
- **Commission lifecycle** (`commission/service/*`): CALCULATED when the order is placed → APPROVED automatically when the order is delivered → PROCESSING by a nightly job after a 10-day hold → PAID via `pay`/`pay-batch`, which records money out and recovers clawbacks. Rejecting is only allowed from CALCULATED or APPROVED. Payouts (`payout/`) are statements (PDF) grouping already-PAID commissions; they don't move money.

**Async and realtime**
- Domain events (`*/event/*Event` + `*Listener`, e.g. `OrderStatusChangedEvent`, `CustomerDuesChangedEvent`, `TripStatusChangedEvent`) fan out to notifications and STOMP topics (`/topic/...`) that the admin subscribes to.
- Scheduled jobs use `@Scheduled` + ShedLock `@SchedulerLock`. Failures surface through `ScheduledJobMonitor`.

**Schema**
- Flyway migrations live in `src/main/resources/db/migration/V<n>__Description.sql`. Use the next free number; check `ls … | sort -V | tail`.
- Hibernate runs with `ddl-auto=validate`, so entity changes need a migration.
- Renaming or removing an enum value needs a data migration too. `ExpenseCategory.FUEL` → `PETROL` once broke `GET /api/expenses` for everyone until V132 fixed the rows.

## Rules (from AGENTS.md, enforced partly by `src/test/java/com/ebee/architecture/ArchitectureTest.java`)

- **Controllers:** call services, never `*Repository` (enforced). No business logic in controllers.
- **Injection:** constructor injection only; no field `@Autowired`/`@Value` (enforced). Prefer `@ConfigurationProperties`.
- **No new Lombok.** Use records for DTOs and explicit constructors elsewhere. The ArchUnit rule for this cannot fail, because Lombok annotations are source-retention and invisible in bytecode. The test passing proves nothing, and much existing code still uses `@RequiredArgsConstructor`/`@Builder`. Don't add more.
- **Services:** keep them single-purpose; split around ~250 lines. No package cycles, and no `@Data` on entities.
- **Tests:**
  - Mockito for service unit tests (`@InjectMocks`; a constructor dependency without a `@Mock` is injected as null);
  - `@WebMvcTest` + `@Import(SecurityConfig…)` + `@WithMockUser(roles=…)` for controller and authorization tests (see `PayoutControllerSecurityTest`);
  - `AbstractIntegrationTest` (Testcontainers Postgres 16) for integration.

## Repo notes

- **Branches and PRs:** branch from `main` and open PRs against `main`; don't stack PRs on feature branches.
- **CI and deploy:** CI runs `./mvnw verify`. Deployment is `deploy.yml` / `deploy.sh` (Docker + nginx; see `DEPLOYMENT.md`).
- **Not source:** `graphify-out/`, `db_plan/`, `backups/`, `archunit_store/` and `target/` aren't application source.
