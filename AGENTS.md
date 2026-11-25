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

**Agent Commission Ledger Tracking (August 9, 2026):**
- Commission lifecycle now fully tracked in `agent_ledger`: CREDIT on CALCULATED, DEBIT on PAID
- `cancelCommissionForOrder()` now correctly handles both PAID (no entry needed) and APPROVED/PROCESSING (DEBIT reversal) states
- `CommissionCalculationService.calculateCommissionForOrder()` now records CREDIT entry via `ledgerService.recordAgentCommission()`
- Added `syncAgentTotalCommission(UUID agentId)` to `CommissionLifecycleService` — recalculates `agent.totalCommission` and `agent.totalSales` from commissions table
- Agent ledger balance now accurately reflects current commission liability: positive = owed, zero = settled

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

## Current State (August 10, 2026)

### Trip Module Fixes (F1–F16)

**WebSocket ownership scoping:**
- `/topic/trip-orders/{driverId}` is now subscribable by ADMIN/SUPER_ADMIN/DRIVER; DRIVER is restricted to their own `driverId` (email → user → driver lookup) in `WebSocketAuthInterceptor.authorizeSubscribe`, mismatches throw `MessageDeliveryException`

**Trip lifecycle hardening:**
- `TripOrderLifecycle` (new component in `trip/service`) centralizes order-state rules: `RESETTABLE_DELIVERY_STATUSES` (PENDING, ASSIGNED, PICKED_UP, IN_TRANSIT, FAILED) and `TERMINAL_ORDER_STATUSES` (CANCELLED, RETURNED, RETURN_EXCHANGE); `detachAndReset()` clears trip/assignee and returns order to PENDING
- `TripService.cancelTrip()` refuses to cancel CANCELLED/COMPLETED trips; detaches+resets only unresolved orders (DELIVERED/FAILED_PERMANENT keep the trip reference), publishes per-order `DeliveryStatusChangedEvent` + `TripStatusChangedEvent`, recalculates driver availability
- `TripService.completeTrip()` and driver `DriverAppService.completeTrip()` now mirror each other: require every order to be completable (`DELIVERED`, `FAILED_PERMANENT`, retryable `FAILED`, or CANCELLED/RETURNED/RETURN_EXCHANGE order status), otherwise reject with `IllegalStateException` listing the offending order numbers; on success, retryable `FAILED` orders are released from the trip via `detachAndReset()` (trip null, delivery back to PENDING, failure reason cleared, per-order `DeliveryStatusChangedEvent` published) so they become eligible for another trip; then publish events + recalc availability
- `TripOrderLifecycle` helpers for the completion guard: `isTerminalForTrip()` (DELIVERED/FAILED_PERMANENT or closed sale), `isRetryableFailed()` (FAILED delivery with an open sale), `isCompletableForTrip()` = terminal OR retryable-FAILED (orders in PENDING/ASSIGNED/PICKED_UP/IN_TRANSIT or with null delivery status still block completion)
- `TripStatusChangedEvent` carries an explicit `orderCount` (from `findByTripId`) instead of the lazy inverse collection
- `DeliveryService.recalculateDriverAvailability()` made public (was private `updateDriverAvailability`) so trip completion/cancellation refreshes availability

**Settlement accuracy:**
- `toTripResponse()` excludes orders whose status is CANCELLED/RETURNED/RETURN_EXCHANGE OR whose deliveryStatus is FAILED_PERMANENT from expected/collected/paid/partial/unpaid totals; only APPROVED expenses count toward `totalExpenses`/`netAmount`

**Driver PII scoping:**
- `getTripById()`/`getTripOrderMarkers()` enforce driver ownership for non-admins; `getActiveTripOrderMarkers()` forces caller's own driverId for DRIVER role

**Validation & guards:**
- `createTrip`/`updateTrip` reject `endDate < startDate`; `updateMileage` rejects negative mileage
- `addOrdersToTrip()` throws `IllegalStateException("Order X is already on trip Y")` when the order belongs to a different trip (no more silent re-assignment)
- `deleteTrip()` blocks deletion when orders OR expenses reference the trip (`countByTripId`)

**Filtering & re-ship (F7):**
- `getAllTrips()`/`getAllTripsPaginated()` use a single `search(status, driverId, dateFrom, dateTo)` query (list + paged, nullable params)
- `OrderServiceImpl.updateOrderStatus()` re-shipping an order with null delivery resets delivery to PENDING and clears failure fields, trip, assignee (unblocks re-shipping stuck orders)

**Cleanup:**
- Removed unused `TripRepository` methods (`findByName`, `findByDriverId`, `findByStatusNotIn`, `findByDriverIdAndStatusNotIn`, paged `findAll`/`findByStatus`/`findByDriverId`/`findByDriverIdAndStatus`)
- `TopicAuthorizationRegistry` allows DRIVER on `/topic/trip-orders`; `WebSocketAuthInterceptor` gained the ownership check
- `addOrdersToTrip()` now requires `OrderStatus.SHIPPED` (dead `FAILED` order-status acceptance removed) in both `TripService` and `DriverAppService`
- `removeOrdersFromTrip()` now fully detaches unresolved orders via `TripOrderLifecycle.detachAndReset()` (clears trip + assignee, resets to PENDING, clears failure reason, publishes `DeliveryStatusChangedEvent`); DELIVERED/FAILED_PERMANENT orders only lose the trip reference
- `TripService.startTrip()` and `DriverAppService.startTrip()` now call `deliveryService.recalculateDriverAvailability()` so the driver is marked ON_TRIP when a trip is started

**Tests:** 487 pass

## Current State (November 25, 2026)

### Customer Ledger Rules (Phase A fix)
The customer ledger is **accounts receivable only**: `balance = SUM(debit) - SUM(credit)` = what the customer owes.
The advance balance is a separate wallet (`customer_advance_transactions`) and is **not** posted to the ledger.

| Event | Entry |
|---|---|
| Order created | `INVOICE` debit (total) |
| Money received (upfront, later, bulk, or from advance wallet) | `PAYMENT` credit |
| Value removed (cancel / return / FAILED_PERMANENT) | `CREDIT_NOTE` credit |
| Overpaid money refunded in cash/bank | `REFUND` **debit** |
| Overpaid money moved to the advance wallet | `ADVANCE_TRANSFER` debit (+ wallet credit) |
| Manual correction | `ADJUSTMENT` debit/credit |
| `ADVANCE` | legacy only — old wallet credits posted to A/R; no longer written |

- `OrderSettlementService` is the single path for cancellation, item returns, approved return/exchange, and `FAILED_PERMANENT`: credit note → refund/advance only the **overpaid** part (`paid - newTotal`) → sync balances. Combo lines expand to component products for stock restore.
- Returns on a partly paid order reduce what is owed first; cash only goes back for real overpayment.
- `FAILED_PERMANENT` now settles money like a cancellation; `refundChoice` is required in `DeliveryStatusUpdateRequest` when the customer has paid.
- Upfront payment at order creation posts a `PAYMENT` ledger entry. Paid amount above the order total is rejected (non-COD).
- `useAdvance` rejects amounts above the order's outstanding and cancelled/returned orders.
- Invariant: `ledger balance == Σ order.outstandingBalance + Σ unlinked entries` (no order/payment/return id, excluding legacy `ADVANCE`). Check with `GET /api/accounting/reconciliation/customers?mismatchesOnly=true` (ADMIN).
- Tests: `OrderSettlementLedgerScenarioTest` runs the real LedgerService + settlement on an in-memory ledger and asserts the invariant per scenario.
- Existing production rows written under the old rules are NOT corrected by this change — run the reconciliation report, review, then post dated `ADJUSTMENT` entries.

### FIFO Costing & Cost of Goods (Phase B fix)
- `InventoryService.deductStock()` returns the actual FIFO cost of the layers consumed per stock row; `createOrder` sets `order_items.cost_price` (per unit, 4 dp) on **every** line — regular, combo (sum of component rows) and free item. Previously only combo/free lines had a cost, so the P&L overstated profit.
- `InventoryService.returnStock()` puts stock back at the line's original `costPrice` (combo components: current average).
- `inventory.average_cost` is a true weighted average of remaining layers (was: last receipt's unit cost).
- Batch stock movements lock rows with `findByProductIdInWithLock` (ordered by product id) and validate the **combined** quantity when a product appears on several lines. `@Retryable` removed from the batch method (it can't retry inside the caller's transaction).
- V123 widens `order_items.cost_price` and `inventory.average_cost` to NUMERIC(14,4).
- COGS = `SUM(cost_price × (quantity − returned_quantity))`, excluding CANCELLED/RETURNED/RETURN_EXCHANGE (same as revenue). `FinancialReport.linesMissingCost` counts sold lines with no cost (pre-fix orders) — backfill is part of the data repair.

### Commissions, Clawbacks & Payouts (Phase C fix)
- Partial return → `CommissionLifecycleService.adjustCommissionForOrder(order)` re-bases the commission on the reduced order total at its stored rate (D1); a full return still cancels it. Called by `OrderServiceImpl.returnOrder` and `ReturnExchangeService` **after** settlement.
- Already-PAID commission that is reduced or cancelled is **clawed back** (D2): agent-ledger debit (balance goes negative = agent owes), `commissions.clawback_amount` and `agents.clawback_balance` increase, `agent.totalCommission` decreases.
- Paying a commission (`markAsPaid` / `payBatch`) first recovers the agent's `clawback_balance`; only the cash actually paid is debited and stored in `commissions.paid_amount` (null on rows paid before V124 → treat as `commission_amount`, see `getNetPaidAmount()`).
- Rejecting a CALCULATED/APPROVED commission now posts the reversing agent-ledger debit.
- Payout statements (`createBatchPayouts`) only include PAID commissions not already linked in `payout_commission_ids`, total = net paid; an empty batch is rejected. Payouts are statements — money moves when a commission is marked PAID.
- V124 adds `agents.clawback_balance`, `commissions.clawback_amount`, `commissions.paid_amount`.
- Tests: `CommissionClawbackScenarioTest` (real lifecycle + payment + ledger on an in-memory agent ledger), `PayoutBatchTest`.

### VAT (Feature 1)
- New `tax` package: `tax_rates` (seeded "VAT 18%" and "Exempt"), single-row `business_settings` (`vat_registered`, `vat_registration_number`, `prices_include_tax`), and a `vat_entries` journal. API: `GET/PUT /api/tax/settings`, `GET/POST/PUT /api/tax/rates`, `GET /api/tax/vat-report?from&to` (ADMIN).
- **Master switch:** while `vat_registered` is false (the default) no VAT is calculated anywhere and every total is unchanged. Turning it on requires a VAT registration number.
- `products.tax_rate_id` (null = exempt), `customers.vat_number`. `suppliers.vat_number` exists in the DB but isn't exposed in the API yet.
- Sales: `TaxCalculator.forUnit()` per unit, rounded to cents. `order_items.unit_price` stays the **gross** price charged; `unit_tax`/`tax_amount` hold the VAT in it; `orders.tax_total` = Σ. Discounts apply before VAT. Combos split their price across components by list price (`forBundle`), each at its own rate. Free items carry no VAT.
- Customer ledger and payments use the gross total (unchanged). Commission is calculated on **net** sales (`totalAmount − taxTotal`).
- Settlement reverses `unit_tax × returned units` on returns (lowers `orders.tax_total`) and VAT on all retained units on cancel, posted as negative `CREDIT_NOTE` VAT entries dated the day of the return — so credit notes reduce VAT in the period they're issued.
- Purchases: PO `unit_price` is net; `unit_tax` = input VAT on top (from the product's rate); supplier owed = gross; FIFO layers stay at net cost. `PURCHASE_RECEIPT` / negative `PURCHASE_RETURN` VAT entries.
- `sumRevenueByDateRange` (P&L, dashboards, sales reports) is now **net of VAT**.
- Invoice PDF becomes "TAX INVOICE" with seller/buyer VAT numbers and a net/VAT breakdown when VAT is on. Invoice and receipt no longer crash on combo lines.

## Agent Workflow

1. Before editing, run `./mvnw clean compile` to verify the baseline.
2. After changes, run `./mvnw test`.
3. Run `./mvnw verify` before committing.
4. If adding a new domain, mirror the existing `order/` or `product/` package layout.
5. Ask the user before running `git commit`, `git push`, or destructive database operations.
