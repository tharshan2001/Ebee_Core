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

### Bank & Cash Accounts, Balance Sheet, Cash Flow (Feature 2)
- New `finance` package: `payment_accounts` (CASH / BANK / CHEQUES_IN_HAND / WALLET), `account_transactions` (IN/OUT, typed, positive amounts; balance = Σ IN − Σ OUT; opening balance is an OPENING transaction) and `payment_account_defaults` (purpose → account). V126 seeds "Cash in hand", "Main bank account", "Cheques in hand" and defaults: CASH/COD/REFUND/EXPENSE → cash, BANK_TRANSFER/CARD_PAYMENT/COMMISSION_PAYOUT → bank, CHEQUE → cheques in hand.
- `MoneyAccountService.recordMovement(direction, amount, purpose, accountId, type, …)` is called by every flow that moves real money: customer payments (`OrderPaymentService.recordPayment`, upfront at order creation, account-level payments in `CustomerService`), refunds (`OrderSettlementService` via `recordPayout`, falling back to the REFUND default), supplier payments (PO payment, PO paid at creation, `SupplierServiceImpl` bulk), expense approval, and the cash part of commission payouts. Purpose = payment method name or flow; an explicit `accountId` (optional on `RecordPaymentRequest`, `OrderRequest`, purchase `RecordPaymentRequest`) overrides it. CREDIT and payments from the advance wallet move no money and record nothing.
- API (ADMIN) under `/api/finance`: accounts CRUD with balances (`?asOf`), `/accounts/{id}/statement?from&to`, `/transfers`, `/cheque-deposits` (cheques-in-hand → bank only), `/owner-deposits`, `/owner-withdrawals`, `/account-defaults`, `/balance-sheet?asOf`, `/cash-flow?from&to`.
- Balance sheet: assets = account balances + customer receivables + stock at cost (**current** FIFO value, not as of the date) + agent clawbacks + supplier advances + VAT refundable; liabilities = supplier payables + commission payable + customer advance wallets + customer credits + VAT payable; equity = difference. Each party's ledger balance is split by sign.
- Cash flow: operating (customers, refunds, suppliers, expenses, commissions), financing (owner money, opening balances), other (adjustments); transfers and cheque deposits excluded.
- Accounts start at zero: money received before V126 isn't in them, so enter real opening balances per account before relying on the balance sheet.

### Locations & Stock Transfers (Feature 3)
- New `location` package. V127 adds `locations` (WAREHOUSE / SHOP, exactly one `is_default`), creates **"Main"** as the default, and moves all existing stock, orders and POs to it. Behaviour is unchanged until a second location is added.
- `inventory` is now one row per **(product, location)** (`uk_inventory_product_location`); `Inventory.product` is `@ManyToOne`, `Product.inventories` is a set. Product responses show `InventoryMapper.toProductSummary()` — one total across locations (`locationName` "All locations" when there's more than one).
- `InventoryService`: every operation has a location-aware form (`deductStock(locationId, …)`, `returnStock(locationId, …)`, `addStock(…)`, `recordPurchaseStocks(locationId, …)`, `recordOrderStocks(locationId, …)`, `checkStockAvailability(locationId, …)`). The old signatures mean the **default location**. Rows are locked per (product, location) in product-id order; inbound moves create the row if the product is new to that location, outbound moves never do. `StockRequest.locationId` targets manual adjustments.
- Orders ship from `OrderRequest.locationId`, else the agent's home location (`agents.location_id`, set via `PUT /api/locations/agents/{agentId}?locationId=`), else the default; stored on `orders.location_id`. Settlement returns stock to the order's location. POs receive into / return from `purchase_orders.location_id` (`PurchaseOrderRequest.locationId`).
- Stock transfers (`stock_transfers`, `stock_transfer_items`): DRAFT → dispatch (`TRANSFER_OUT`, FIFO cost stored per line) → IN_TRANSIT → receive (`TRANSFER_IN` at the destination at the **same unit cost**) → RECEIVED. Cancel from DRAFT (nothing moves) or IN_TRANSIT (stock goes back to the source at its cost).
- API under `/api/locations`: list/create/update locations, `GET /stock/{productId}` (per location), agent home location, `/transfers` (list, get, create, `/{id}/dispatch`, `/{id}/receive`, `/{id}/cancel`).
- Tests: `StockTransferServiceTest`, `LocationServiceTest`, `InventoryLocationTest`.

### Business-rule fixes (Phases D–G)
- **Purchase orders:** cancelling is only allowed from DRAFT / PENDING / APPROVED. Once stock has arrived, use `POST /api/purchase-orders/{id}/return-items` or the new `POST /{id}/close-short?reason=` (PARTIALLY_RECEIVED → RECEIVED; total becomes the value received; rejected if already paid more than that). RECEIVED / RETURNED / PARTIALLY_RECEIVED can't be set through the status endpoint.
- **Direct returns** (`POST /api/orders/{id}/return`) require a DELIVERED order, like return/exchange requests; undelivered orders are cancelled instead.
- **Accounting periods:** `recordPeriodTransaction` creates the current period (and its fiscal year) on first use and throws `PeriodClosedException` (409) when the period is CLOSED/ARCHIVED — the whole operation rolls back instead of silently dropping the journal entry. The monthly job used to call `openPeriod(month, year)` with swapped arguments and never opened anything; it now uses the same `ensurePeriod`.
- **Payments:** customer-level bulk payments apply to combo-only orders too (LEFT JOIN product) and skip CANCELLED/RETURNED orders and orders with nothing outstanding. `recordPayment` no longer overwrites `order.paymentMethod` (each `Payment` row records its own method).
- **Order status:** manual updates only move forward (PENDING → CONFIRMED → PROCESSING → SHIPPED, steps may be skipped); FAILED / RETURN_EXCHANGE can't be set manually. Re-shipping a SHIPPED order whose delivery was cleared is still allowed. `OrderStatus.RETURN_EXCHANGE` is never set by the code; it's kept for existing rows.

### Lots & Expiry (Feature 4)
- V128: `products.tracks_expiry`; `inventory_layers.lot_number / expiry_date / manufactured_date`; `order_item_lots` (which batch each order line took: product, layer, lot, expiry, qty, unit cost, returned qty).
- PO receipt: `ReceiveOrderRequest.ItemReceipt` takes optional `lotNumber`, `expiryDate`, `manufacturedDate`; `expiryDate` is **required** for products with `tracksExpiry` (product create/update param). Same rule for any inbound batch with lot info.
- Consumption (`InventoryService.consumeLayers`): tracked products are taken **earliest expiry first** and expired batches are skipped (never sold — "Not enough unexpired stock"); untracked products stay FIFO by receipt time.
- Order creation uses `deductStockWithLots` and saves an `OrderItemLot` per batch per line (combo lines: one per component batch).
- Settlement returns put units back **into the lots they were sold from** (latest taken first) at that lot's cost, lot number and expiry, and bump `order_item_lots.returned_quantity`; units without a recorded lot (pre-V128 orders) fall back to the line cost.
- `GET /api/inventory/expiring?days=30&locationId=`, `POST /api/inventory/expired/write-off?locationId=` (movement type `EXPIRED`; never below reserved quantity), `GET /api/inventory/lots/{productId}/{lotNumber}/sales` (recall: orders + customers that got the lot).
- `ExpiryAlertScheduler` (07:00 daily, ShedLock `expiryAlerts`): in-app `STOCK_EXPIRING` alert to admins for expired batches and batches expiring within 30 days.

### CSV / Excel Import (Feature 5)
- New `importer` package (controller / entity / repository / service — importers live under `importer.service` so the layered-architecture ArchUnit rule allows them to use services and repositories). Libraries: `commons-csv`, `poi-ooxml` (.xlsx only; .xls is refused).
- `POST /api/imports/{type}?dryRun=true|false` (multipart `file`, ADMIN), `GET /api/imports/{type}/template` (CSV header + sample row), `GET /api/imports` (history in `import_jobs`, V129). Types: `products`, `opening-stock`, `customers`, `suppliers`.
- Flow (`ImportService`): read (headers normalised to snake_case, max 5,000 rows) → required columns present → importer validates **every** row → dry run stops here → otherwise `ImportApplier` applies all rows in **one transaction** (any failure → nothing written, status FAILED). Any row error blocks the whole file. Unknown columns are reported, not fatal.
- Products: upsert by `item_code`; missing categories are created; `vat_rate` matches a tax rate name; `tracks_expiry`.
- Opening stock: one batch per row at `unit_cost` with lot / expiry (movement type `OPENING`, `InventoryService.addOpeningStock`); refused when the product already has stock or stock history at that location (re-importing can't double stock); expiry required for tracked products.
- Customers: upsert by normalised phone; created **without** the welcome SMS/email (`createCustomer(request, false)`); `credit_limit`; `opening_balance` posted once as an ADJUSTMENT (positive = owes us, negative = credit) with description "Opening balance (import)".
- Suppliers: matched by name (existing ones aren't changed); `opening_balance` posted once — positive = supplier invoice (we owe), negative = adjustment (prepaid).

### Price Groups & Units (Feature 6)
- V130: `price_groups`, `product_group_prices` (product × group price, per stock unit), `customers.price_group_id`; `units` (seeded Box, Pack, Dozen, Carton, Bundle), `product_units` (product × unit, `units_per_pack` > 1, optional fixed pack `price`, `is_active`); `order_items.sales_unit_id / sales_unit_name / units_per_sales_unit` (default 1).
- `PricingService.quote(product, priceGroupId, productUnitId)` is the one price lookup for order lines: per-unit price = the active group's price for the product, else `product.price`; a pack = its own price if set, else per-unit × `units_per_pack`. A pack of another product or an inactive pack is rejected.
- Order lines are counted **in the unit they were sold in**: `quantity` = packs, `unit_price` = per pack (`OrderItemRequest.unitId` picks the pack). Stock moves `quantity × units_per_sales_unit` (`OrderItem.toStockUnits`); returns put back stock units at line cost ÷ pack size (lot returns unchanged). FIXED catalog discounts and FIXED per-order discount limits scale with the pack size; PERCENTAGE ones don't need to.
- Sales-quantity report queries (`OrderItemRepository`, `ProductRepository` best-sellers) sum `quantity × units_per_sales_unit`. The invoice PDF shows "(Box of 12)" on pack lines.
- API under `/api/pricing`: `/groups` (list/create/update), `PUT|DELETE /groups/{groupId}/products/{productId}?price=`, `GET /products/{productId}/group-prices`, `PUT /customers/{customerId}/group?groupId=` (omit to clear), `/units` (list/create), `GET /products/{productId}/units`, `PUT /products/{productId}/units/{unitId}`, `GET /quote?productId&customerId&productUnitId`.
- Tests: `PricingServiceTest`, pack cases in `ProductDiscountTest`, `OrderServiceTest`, `OrderSettlementLedgerScenarioTest`.

### Correctness pass (Phase 2)
- **Scheduled-job failures are visible:** `common.service.ScheduledJobMonitor` is an aspect around every `@Scheduled` method (outermost, so it also sees commit/rollback failures). A failing job is logged and admins get an in-app `SCHEDULED_JOB_FAILED` alert (throttled to one per job per hour). Jobs that skip bad items and carry on (reminders) call `reportPartialFailure(job, failed, total, firstError)`. Don't wrap a scheduled job body in `try/catch (Exception)`; let it propagate.
- Commission auto-processing (`processApprovedCommissions`) no longer swallows per-item errors inside its transaction: any failure rolls back the run and alerts.
- The remaining `catch (Exception …)` blocks are deliberate best-effort isolation (notification sends, WebSocket broadcasts, audit-log writes, auth token parsing) — a failed SMS/broadcast must not fail the order. Narrowed where a specific type exists (specifications JSON → `JsonProcessingException`, record-to-map reflection → `ReflectiveOperationException`).
- **Stock row locking:** every `InventoryService` path that changes `quantity` or `reserved_quantity` (`adjustStock`, `recordOrderStock`, `reserveStock`, `releaseReservedStock`, `confirmOrderStock`, plus the batch paths) loads the row with `findByProductIdWithLock` (PESSIMISTIC_WRITE), so concurrent changes queue instead of failing on version conflicts.
- **Exact commission values:** `CommissionConfigRequest/Response` (`commissionRate`, `minAmount`, `maxAmount`), `AgentUpdateRequest`, `AgentResponse`, `AgentWithCredentialsResponse` and the agent commission-rate endpoints use `BigDecimal` instead of `Double`. JSON stays numeric but may show trailing zeros (e.g. `5.00`).
### Real-database tests (Phase 3)
- `support.RealDatabaseTest` is the base for tests that run the real services on the real PostgreSQL schema (Flyway, constraints, locks): locally the dev database from `application.yml`, in CI the Postgres service container (`SPRING_DATASOURCE_*`). It signs in a fresh admin and has fixtures (`customer()`, `agent()`, `order(...)`, `product(...)`). Make tests `@Transactional` so they roll back; concurrency tests can't be, so they delete their own rows in `@AfterEach`. Same context config as `CommissionLockTest`, so Spring reuses one context.
- Suites: `AccountingPeriodIntegrationTest`, `PurchaseOrderReceivingIntegrationTest`, `InventoryConcurrencyIntegrationTest` (two buyers for the last unit, 8 buyers for 5 units, reserve/confirm/release at FIFO cost), `CustomerDuesIntegrationTest` (real order → payments → cancel, checked with the customer reconciliation), `PayoutIntegrationTest`.
- JaCoCo now also enforces per-package floors where money moves (accounting/tax 70%, order/inventory/finance 60%, payout/commission/purchase 50%; whole bundle 25%). Raise them as coverage grows; never lower them to get a build through.
- Bugs these tests found and fixed:
  - **Accounting periods could never be written on PostgreSQL** (V131): status columns were the native enum `period_status` while the entity sends text, and the audit row never filled `year`. Since postings create the month's period on first use, the first posting of every new month failed. Statuses are now checked VARCHAR; `openPeriod` sets start/end dates.
  - Ledgers now check the period is open (`AccountingPeriodService.requireOpenCurrentPeriod`) **before** writing their row, not after.
  - Re-closing a reopened period counted the previous `PERIOD_SNAPSHOT` in the new snapshot's totals.
  - **Purchase orders:** receiving added the received value on top of the outstanding balance that was already "total − paid" (a 1,000 credit PO showed 2,000 owed); money paid when the PO was placed never reached the supplier ledger (a fully prepaid PO left the supplier showing 1,000 owed); closing short left the old outstanding. Now: outstanding = total − paid from the start (POs without a payment method start at first receipt), every payment (upfront or later) is a supplier-ledger payment, every receipt is a supplier-ledger invoice, close-short sets outstanding = received − paid. `suppliers.outstanding_balance` is now recalculated from the supplier ledger (floored at 0; a prepaid supplier has a negative ledger balance) instead of being added to and clamped separately. Removed the unused `addSupplierOutstanding` / `subtractSupplierOutstanding` / `releaseSupplierOutstanding` helpers.
  - **Order creation errors came back as HTTP 500:** because `OrderServiceImpl` has a `@Recover` for duplicate order numbers, Spring Retry wrapped every other failure (out of stock, credit limit, missing district) in `ExhaustedRetryException("Cannot locate recovery method")`. A catch-all `@Recover` now rethrows the original error.

### Structure (Phase 4)
- **Orders are split by job.** `OrderServiceImpl` keeps order creation (plus PDF/payment pass-throughs); `OrderQueryService` has the read-only lookups and listings; `OrderLifecycleService` has status moves, cancellation and returns. The `OrderService` interface is unchanged and `OrderServiceImpl` delegates, so controllers don't care. Unit tests that build `OrderServiceImpl` with `@InjectMocks` also `@InjectMocks` the two new services and set them on it (see `wireSplitOrderServices`). Put new read methods in `OrderQueryService` and new post-creation state changes in `OrderLifecycleService`.
- **Trips:** `TripService` keeps planning and the trip lifecycle (create, update, cancel, delete, start, add orders, mileage, complete) and hands lookups to `TripQueryService`; `TripViews` builds trip responses and map markers and checks a driver may see a trip — use it rather than re-building responses.
- **No circular dependencies.** The last two `@Lazy` setters are gone (`AccountingPeriodService` never used its `LedgerService`; `OrderPaymentService` reads the customer's balance from `LedgerService` instead of calling back into `CustomerService`). Spring Boot's default ban on circular references now catches new cycles at startup — don't add `@Lazy` or `allow-circular-references` to get around one; publish an event or move the shared piece down.
- **One `/api/reports` controller:** the old `reporting` package (`/summary`, `/trial-balance`, `/aging`) is merged into `report` (`report/controller/ReportsController`). Same URLs.
- **Dependabot** (`.github/dependabot.yml`): weekly Maven, GitHub Actions and Docker updates; Spring / test libraries grouped; Spring Boot major versions ignored (planned upgrades).
### Bulk messaging in batches
- `MessageService.sendMessage` and `MessageDispatcher.dispatch` handle recipients `MessageService.BATCH_SIZE` (500) at a time and flush + clear the persistence context after each batch, so a message to every customer never holds them all in memory. Pages are read in id order.
- `RecipientResolver.resolveInBatches` is the entry point; `AllCustomersRecipientResolver` / `AllAgentsRecipientResolver` page the database. Selected / manual lists use the default (split a bounded list). A new whole-population resolver must override `resolveInBatches` — don't load the population with `findAll()`.
- Tests: `BulkMessagingBatchesTest` (paging, dispatcher), `BulkMessagingIntegrationTest` (real DB, > 1 batch).

## Agent Workflow

1. Before editing, run `./mvnw clean compile` to verify the baseline.
2. After changes, run `./mvnw test`.
3. Run `./mvnw verify` before committing.
4. If adding a new domain, mirror the existing `order/` or `product/` package layout.
5. Ask the user before running `git commit`, `git push`, or destructive database operations.
