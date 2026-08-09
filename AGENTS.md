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
- `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET_NAME`
- `GMAIL_USERNAME`, `GMAIL_APP_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`

## Security Notes

- Role hierarchy: `ROLE_SUPER_ADMIN > ROLE_ADMIN > ROLE_AGENT`
- Public endpoints are configured in `SecurityConfig.java`
- CORS origins are configured via `app.cors.allowed-origins`
- IP whitelisting reads `X-Forwarded-For`/`X-Real-IP`; ensure the app runs behind a trusted reverse proxy

## Current State (August 7, 2026)

### FIFO-Layered WAC Implemented
- New `inventory_layers` table (V79 migration) stores per-receipt cost layers: `quantity`, `remaining_quantity`, `unit_cost`, `received_at`
- `InventoryLayer` entity + `InventoryLayerRepository` with queries for FIFO consumption ordering by `received_at ASC`
- `recordPurchaseStocks()` inserts a new layer with the received quantity and unit cost, recalculates WAC from all remaining layers
- `recordOrderStock(s)` with OUT consumes from oldest layers first (FIFO), recalcs WAC
- `recordOrderStock(s)` with RETURN adds a new layer at current WAC
- `confirmOrderStock()` consumes FIFO layers and records stock movement with reserved history
- Private helpers: `consumeFifoLayers()`, `recalculateWac()`
- `stock` table tracks reserved quantity snapshots (`reserved_quantity_before`, `reserved_quantity_after`) via V120 migration
- `StockMovementRecorder.record()` overloaded to accept reserved before/after params
- `POST /api/inventory/confirm-order-stock` endpoint releases reserved stock + deducts quantity (ADMIN only)
- `order_items.cost_price` snapshots the WAC at order time (before FIFO consumption), giving a reasonable COGS approximation

### Curl-Verified
- Two receipts at different costs (100, 200) → two layers with correct remaining qty
- WAC correctly calculated from layers: (10×100 + 10×200)/20 = 150
- FIFO consumption: selling 5 consumes from oldest layer (10→5 remaining), WAC recalculates to 166.67
- Further sale of 6 exhausts layer 1 (5→0) and consumes 1 from layer 2 (10→9), WAC = 200
- Reserve 3 units: reservedBefore=25, reservedAfter=28 ✓
- Confirm order stock: OUT qty=-3, reservedBefore=28, reservedAfter=25 ✓
- 308 tests pass

### Manual Outstanding Adjustment
- `POST /api/customers/{id}/adjust-outstanding` endpoint for admins to manually add outstanding
- Request body: `{ amount, date, description }` — amount must be positive
- `effective_date` stored in `customer_ledger` for audit trail
- Broadcasts to `/topic/customer-dues` WebSocket topic for real-time UI updates
- Only increases outstanding (no deduction)

### Supplier Outstanding Management
- `supplier_ledger` now properly updated on PO receive, payment, and return
- `effective_date` and `reference` fields added to `supplier_ledger` (V118 migration)
- `syncSupplierOutstanding()` uses ledger balance calculation (matching customer pattern)
- New endpoints:
  - `GET /api/suppliers/with-outstandings` — list suppliers with outstanding balance > 0
  - `GET /api/suppliers/{id}/outstanding-orders` — get PO breakdown with outstanding amounts
  - `POST /api/suppliers/{id}/payments` — pay supplier desired amount (FIFO across POs)
  - `POST /api/suppliers/{id}/adjust-outstanding` — manual outstanding adjustment
- WebSocket topic `/topic/supplier-dues` configured for ADMIN/SUPER_ADMIN

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
- 413 tests pass

### Order Return & Exchange Overhaul (Item-Level Returns)
- **Item-level returns**: `order_items.returned_quantity` tracks per-item return state (V121 migration)
- `OrderItem.getReturnableQuantity()` = `quantity - returnedQuantity`; `isFullyReturned()` checks full return
- `POST /api/orders/{id}/return` accepts optional `items` list (`ReturnOrderItemRequest`): null/empty = return all, otherwise specify `{orderItemId, quantity}` per item
- `POST /api/orders/{orderId}/return-exchange` accepts item-level `InitiateReturnExchangeRequest`: `{items: [{orderItemId, quantity}], reason}`
- `POST /api/orders/{returnExchangeId}/return-exchange/review` — all 4 resolutions work: `ADVANCE`, `CASH`, `BANK_TRANSFER` (approve), `REJECTED` (reject)
- **Partial returns**: order stays `DELIVERED` until ALL items returned, then becomes `RETURNED`
- **Order financial adjustment**: `order.totalAmount` reduced by returned items' subtotal; `paidAmount` and `outstandingBalance` recalculated
- **Combo returns**: whole combo only — expands to constituent products via `ComboItem` for inventory restore
- `return_exchange_requests.order_id` UNIQUE dropped; partial unique index allows resubmission after rejection
- `return_request_items` table stores item-level detail per return request
- `ReturnExchangeService` handles: commission cancellation, notifications, audit logging, `CustomerDuesChangedEvent`, `OrderStatusChangedEvent`
- `LedgerService.recordReturnExchangeCredit()` now records accounting period transactions
- `InventoryService.updateCurrentCost()` renamed to `updateCostFromLatestLayer()`
- 10 reporting queries in `OrderRepository` now exclude `RETURN_EXCHANGE` status
- `OrderItemResponse` exposes `returnedQuantity` and `returnableQuantity`
- 442 tests pass (21 in `ReturnExchangeServiceTest`)

### Curl-Verified Return Flows
- Initiate return exchange → PENDING_REVIEW with item-level tracking ✓
- Review ADVANCE on PAID order → paid=0, advance credited, total=0, RETURNED ✓
- Review CASH on PAID order → paid=0, paymentStatus=REFUNDED, total=0, RETURNED ✓
- Review BANK_TRANSFER → APPROVED, same financial adjustments ✓
- Review REJECTED → order stays DELIVERED, returnedQuantity unchanged ✓
- Resubmit after rejection → new PENDING_REVIEW created ✓
- Direct return with REFUND → RETURNED, paid=0, REFUNDED ✓
- Direct return with CREDIT_ADVANCE → RETURNED, advance credited ✓
- Direct return with empty items → returns all (backward compatible) ✓
- Edge: already returned → "Cannot return...already RETURNED" ✓
- Edge: exceeds returnable qty → "Cannot return 5...Returnable: 1" ✓
- Edge: return exchange on RETURNED order → "Can only initiate...DELIVERED" ✓

### Accounting Integrity Fixes (August 9, 2026)

**Money Leak Fixes (Phase 1):**
- `OrderPaymentService.handlePaymentRefund()` now records `customer_ledger` REFUND entry when refunding
- `ReturnExchangeService.reviewReturnExchange()` now records REFUND entry for CASH/BANK_TRANSFER resolutions (ADVANCE path already handled)
- `PurchaseOrderPaymentService.initPaymentOnReceipt()` now uses `addSupplierOutstandingWithLedger()` instead of bare `addSupplierOutstanding()` — supplier ledger entry created on PO receipt
- `CustomerService.manualAdjustAdvance()` now records `customer_ledger` ADVANCE entry
- `CommissionPaymentService.markAsPaid()` and `payBatch()` now record `agent_ledger` DEBIT entries
- `CommissionLifecycleService.cancelCommissionForOrder()` now records reversing `agent_ledger` DEBIT entry when cancelling a PAID commission

**Period Transaction Coverage (Phase 2):**
- `LedgerService.recordCustomerAdjustment()` now calls `accountingPeriodService.recordPeriodTransaction()`
- All 4 supplier ledger methods (`recordSupplierInvoice`, `recordSupplierPayment`, `recordSupplierExpense`, `recordSupplierAdjustment`) now accept `UUID recordedBy` and call `recordPeriodTransaction()`
- Both agent ledger methods (`recordAgentCommission`, `recordAgentPayout`) now accept `UUID recordedBy` and call `recordPeriodTransaction()`

**Data Integrity (Phase 3):**
- Removed `updateCustomerOutstandingBalance()` arithmetic from `initPayment()` — `customer.outstandingBalance` is now ONLY set by `syncCustomerOutstanding()` which reads from ledger (single source of truth)
- V122 migration adds unique index `idx_customer_ledger_order_invoice` on `(customer_id, order_id, transaction_type)` for idempotency
- V122 migration adds indexes on `effective_date` columns and `accounting_period_transactions(period_id, transaction_type)`
- `@Positive` validation added to all ledger amount parameters in `LedgerController`

**Critical Bug Fixes (August 9, 2026):**
- `handleOrderSettlement()` REFUND path now records `customer_ledger` REFUND entry — previously left original invoice DEBIT unreversed
- `cancelCommissionForOrder()` now records CREDIT (reversal) instead of DEBIT — was creating negative agent ledger balance
- Commission payment/cancellation flows now pass `securityService.getCurrentUserId()` instead of `null` for `recordedBy` — was causing DB NOT NULL constraint violation
- Added `syncAdvanceBalance()` and `syncAllBalances()` to `CustomerService` — recalculates `advanceBalance` from `customer_advance_transactions` table

**Accounting Completeness (August 9, 2026):**
- Partial returns now record `customer_ledger` CREDIT adjustment entry when `order.totalAmount` is reduced — eliminates permanent discrepancy between ledger and order outstanding
- New `LedgerService.recordCustomerCreditAdjustment()` method for return adjustments
- `FinancialReportService.getProfitLoss()` now uses FIFO cost from `order_items.costPrice` instead of `product.costPrice` — accurate COGS
- New `OrderItemRepository.sumCostOfGoodsSold()` query
- `AccountingPeriodService.closePeriod()` captures balance snapshot before closing (PERIOD_SNAPSHOT transaction)
- `AccountingPeriodService.autoOpenCurrentMonthPeriod()` scheduled job runs 1st of every month at 1:00 AM — auto-creates fiscal year if missing
- Aging report already existed at `GET /api/reports/aging` with 30/60/90/120+ day bucketing

**Method Signature Changes:**
- `LedgerService.recordSupplierInvoice()` — added `UUID recordedBy` parameter
- `LedgerService.recordSupplierPayment()` — added `UUID recordedBy` parameter
- `LedgerService.recordSupplierExpense()` — added `UUID recordedBy` parameter
- `LedgerService.recordSupplierAdjustment()` — added `UUID recordedBy` parameter
- `LedgerService.recordAgentCommission()` — added `UUID recordedBy` parameter
- `LedgerService.recordAgentPayout()` — added `UUID recordedBy` parameter

**New Dependencies Injected:**
- `CommissionPaymentService` — now depends on `LedgerService`, `SecurityService`
- `CommissionLifecycleService` — now depends on `LedgerService`, `SecurityService`
- `SupplierServiceImpl` — now depends on `SecurityService`
- `PurchaseOrderPaymentService` — now depends on `SecurityService`

**Tests:** 440 pass (2 pre-existing CommissionLockTest failures need DB)

## Agent Workflow

1. Before editing, run `./mvnw clean compile` to verify the baseline.
2. After changes, run `./mvnw test`.
3. Run `./mvnw verify` before committing.
4. If adding a new domain, mirror the existing `order/` or `product/` package layout.
5. Ask the user before running `git commit`, `git push`, or destructive database operations.
