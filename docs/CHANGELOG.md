# Changelog

All notable changes to the Ebee backend are documented here. Dates in YYYY-MM-DD format.

## [Unreleased] — 2026-07-25

### Real-Time WebSocket Broadcasts

- **Order feed** — New `OrderStatusChangedEvent` (Spring ApplicationEvent) published by `OrderServiceImpl` on create, status update, cancel, and return. `OrderStatusChangedEventListener` fires `@TransactionalEventListener(AFTER_COMMIT)` and broadcasts `OrderStatusChangedMessage` to `/topic/orders`. Only `ADMIN` and `SUPER_ADMIN` roles may subscribe.
- **Delivery feed** — `DeliveryStatusChangedEventListener` broadcasts `DeliveryStatusChangedMessage` to `/topic/deliveries`. Available to `ADMIN`, `SUPER_ADMIN`, `AGENT`, and `DRIVER` roles.
- **WebSocket auth** — `WebSocketAuthInterceptor` validates JWT on STOMP CONNECT and enforces role-based SUBSCRIBE access. `TopicAuthorizationRegistry` holds topic→role mappings (new topics added by registering, not editing the interceptor).
- **CORS** — `/ws/**` added to `SecurityConfig.corsConfigurationSource()`. `frameOptions.sameOrigin()` enables SockJS iframe transport.
- **Front-end** — Singleton STOMP client (`stompClient.ts`), generic `useStompFeed` hook, `useOrderFeed`, `useDeliveryFeed`, and `useDriverTracking` wrappers (all sharing one WebSocket connection). Optimistic mutations on Orders, OrderDetail, and Delivery pages. Delivery Kanban auto-refreshes on `SHIPPED` order status.
- **JavaDoc** — All 9 WebSocket-related backend classes documented.
- **`WEBSOCKET_API.md`** — Full API documentation added to `docs/`, including the `/topic/driver-locations` live tracking feed.

## [Unreleased] — 2026-07-15

### Combo (Product Bundle) Feature

- **Combo domain** — New `combo/` package: `Combo`, `ComboItem`, `ComboRule` entities with Flyway V89 (`combos`, `combo_items`, `combo_rules`) and V90 (`combo_id` FK + indexes on `order_items`).
- **Rule types** — Three modes: null (plain bundle), `FREE_PRODUCT` (admin picks exact free product), `DISCOUNT` (`FIXED` or `PERCENTAGE` off combo price).
- **Auto-calculated combo price** — `sum(product.price * quantity)` across all combo items. No manual pricing.
- **Admin CRUD** — `POST /api/admin/combos`, `PUT /api/admin/combos/{id}`, `DELETE /api/admin/combos/{id}`, `GET /api/admin/combos`. Public `GET /api/combos` for browsing.
- **Combo immutability** — Once a combo is used in an order, edits clone a new combo (new UUID) and deactivate the old one. Delete throws `ComboImmutabilityException` (400).
- **Order integration** — New `comboId` field on `OrderItemRequest`. Combos appear as a single order line; free products added as separate lines at price 0. `originalUnitPrice` stored for before/after discount display.
- **Stock flow** — `InventoryService.checkStockAvailability()` (read-only) validates stock before building order items. Single `recordOrderStocks()` call deducts all products (combo items + free product) in one pass with FIFO layer consumption.
- **Cost price on combo items** — Combo order items store `costPrice = sum(avgCost * quantity)` across combo products for COGS tracking.
- **Security** — `/api/combos/**` added to public GET endpoints alongside products and categories.
- **GlobalExceptionHandler** — `ComboImmutabilityException` mapped to 400 Bad Request.
- **38 new tests** — `ComboServiceTest` (19), `ComboControllerTest` (11), `OrderServiceComboTest` (8). 351 total tests pass.

## [Unreleased] — 2026-06-25

### Customer Payment System

- **Payment methods enum** (`PaymentMethod`) — `CASH`, `COD`, `BANK_TRANSFER`, `CREDIT`, `CARD_PAYMENT`. Payment method required at order creation.
- **Payment status enum** (`PaymentStatus`) — `UNPAID`, `PARTIALLY_PAID`, `PAID`, `REFUNDED`. Tracks lifecycle of order payments.
- **Payment fields added to Order** — `paymentMethod`, `paymentStatus`, `paidAmount`, `paidAt`, `outstandingBalance`. Flyway V66.
- **Credit fields added to Customer** — `creditLimit`, `outstandingBalance`. Updated automatically on credit orders/payments.
- **Auto-payment on order create** — `CASH`, `CARD_PAYMENT`, `BANK_TRANSFER` auto-mark `PAID`. `CREDIT` supports flexible partial payment at creation.
- **Record payment endpoint** — `POST /api/orders/{id}/payment` for subsequent payments (full or partial). Available to ADMIN and AGENT roles.
- **Finance summary split** — `collectedRevenue` (PAID orders only) and `outstandingRevenue` tracked separately. `netProfit` now based on collected revenue, not total revenue.
- **Receipt template updated** — Dynamically shows payment status (PAID / UNPAID / PARTIALLY_PAID) and outstanding balance on printed receipts.
- **Migration V66** — Adds payment columns to `orders` table and credit columns to `customers` table with defaults.

## [Unreleased] — 2026-06-21

### CRITICAL Security & Data Integrity Fixes

- **Plaintext password log removed** (`SuperAdminSeeder`) — Super admin password no longer written to application logs on startup.
- **JWT reset token key separated from main secret** (`JwtService`) — Password reset tokens now use a dedicated signing key (`JWT_RESET_SECRET`). The app fails fast at startup if `JWT_RESET_SECRET` is not configured. Access and refresh tokens now include `type` claims to prevent misuse.
- **Refresh token rotation implemented** (`AuthService`, `User`) — Old refresh tokens are invalidated when new ones are issued. Stolen refresh tokens can no longer be reused indefinitely. SHA-256 hash stored in new `refresh_token_hash` column.
- **OTP stored as SHA-256 hash** (`OtpService`) — OTPs are no longer stored in plaintext in Redis. Verification uses `MessageDigest.isEqual()` to prevent timing attacks.
- **OTP attempts TTL on first failure** (`OtpService`) — The failed-attempts counter now expires after 5 minutes of inactivity, preventing indefinite Redis key accumulation.
- **CSRF cookie hardened** (`AuthController`) — `Secure` flag on refresh cookie is now conditional on `request.isSecure()`, enabling development over HTTP.
- **Account lock/expiration enabled** (`UserPrincipal`) — `isAccountNonLocked()` now checks `UserStatus.LOCKED`; `isCredentialsNonExpired()` checks `mustChangePassword`.
- **DisabledException for inactive accounts** (`UserDetailsServiceImpl`) — Inactive accounts now throw `DisabledException` instead of `UsernameNotFoundException`, enabling proper Spring Security error flows.
- **Rate limiter thread pool cleanup** (`RateLimitingService`) — Added `@PreDestroy` to shut down the cleanup scheduler.
- **JwtAuthenticationFilter bean deduplicated** (`SecurityConfig`) — Removed double filter instance.
- **JWT secret defaults removed** (`application.yml`) — `JWT_SECRET` and `JWT_RESET_SECRET` now fail fast at startup if not configured.

### Critical Bug Fixes

- **CascadeType.ALL removed from Product→OrderItem** (`Product`) — Deleting a product no longer cascade-deletes order history.
- **District enum stored as STRING** (`Agent`, `Address`) — `District` enum now uses `@Enumerated(EnumType.STRING)` instead of ORDINAL, preventing data corruption if enum values are reordered.
- **Monetary fields changed to BigDecimal** (`Agent`, `CommissionConfig`) — `commissionRate`, `totalSales`, `totalCommission`, `minAmount`, `maxAmount` changed from `Double` to `BigDecimal` to eliminate floating-point precision loss. All downstream services updated accordingly.
- **S3 operations reordered after DB save** (`AgentService`, `ProductService`) — S3 uploads/deletes now happen AFTER database writes, preventing orphaned files on transaction rollback.
- **Driver stuck in ON_TRIP fixed** (`DeliveryService`) — Driver availability is now freed when a delivery transitions to FAILED.
- **Agent self-approval removed** (`ExpenseService`) — Agents can no longer auto-approve their own expenses. Only ADMIN role auto-approves.
- **Rejected expense fields fixed** (`ExpenseService`) — Rejected expenses no longer set `approvedBy`/`approvedAt`.
- **Search+category filter fixed** (`ProductController`, `ProductRepository`, `ProductService`) — Category filter now works correctly when a search query is active.
- **Unused `stock` parameter** removed from controller signatures where unimplemented.
- **Missing `@Transactional` added** (`CustomerService.findOrCreateByPhone`, `AuthService.forgotPassword`).

### Controller & API Consistency

- **Global exception handlers added** (`GlobalExceptionHandler`) — Added handlers for `MissingServletRequestParameterException`, `HttpMessageNotReadableException`, `HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`, and `ConstraintViolationException`.
- **Authorization added to AlertController** — All alert endpoints now require authentication.
- **Ownership checks added to ReminderController** — ADMIN users can no longer modify other ADMINs' reminders.
- **PageResponse.from() factory added** — Eliminates duplicated Page→PageResponse mapping in 5+ controllers.
- **SortUtils.parse() utility created** — Eliminates duplicated sort-parsing logic in 7 controllers.
- **DELETE endpoints return 204 No Content** — Standardized across Agent, Category, Product, User, AgentApplication controllers.
- **BackupController.createBackup returns 201 Created** — Instead of 200 OK.
- **`ApiResponse` now includes `timestamp` field** — All responses include a timestamp.
- **CommissionController pagination standardized** — Now uses `PageResponse` like other controllers.

### Notification & Messaging Fixes

- **LazyInitializationException prevented** (`NotificationService`) — All `@Async` notification methods now annotated with `@Transactional(readOnly = true)` to open a persistence context on the async thread.
- **Bean Validation added to MessageRequest** — Required fields (`subject`, `body`, `targetType`) now validated.
- **MD5 replaced with SHA-256** (`RichCommunicationSmsSender`) — SMS API password digest now uses SHA-256.
- **XSS prevention in email templates** (`NotificationTemplateService`) — `unescapedKeys` restricted to a whitelist of safe keys (`link`, `buttonText`).
- **Null guard added to template resolution** — `HtmlUtils.htmlEscape(null)` no longer throws NPE.
- **N+1 save bug fixed** (`MessageDispatcher`) — Recipients now saved in a single `saveAll()` call instead of individual saves.
- **RuntimeExceptions replaced** (`MessageService`) — `"Message not found"` now returns 404 (`ResourceNotFoundException`); `"Access denied"` now returns 403 (`AccessDeniedException`).
- **Message double-save removed** (`MessageService`) — Message entity is now saved once instead of twice.
- **RestTemplate timeouts configured** (`NotificationConfig`) — 5s connect and 10s read timeouts set.
- **@Override annotations added** (`NotificationService`) — All interface implementation methods now properly annotated.
- **PhoneUtils enhanced** — Handles `+94` and `0094` international prefixes; made `final` with private constructor.

### Data Integrity

- **Unique constraint on AgentApplication.email** — Prevents duplicate applications.
- **Unique constraint on Driver.licenseNumber** — License numbers now enforced unique.
- **decrementTotalOrders added** (`CustomerRepository`) — Order cancellation now decrements the counter cache.
- **Order validation added** (`Order`) — `@PrePersist`/`@PreUpdate` validation ensures at least one of agent or customer is set.
- **@Repository annotations added** — Added to 4 repository interfaces missing them.
- **@Data replaced on all entities** — All 25 entities now use `@Getter @Setter @EqualsAndHashCode(onlyExplicitlyIncluded = true)` to prevent Hibernate proxy issues.

### Config & Infrastructure

- **AsyncConfig double executor fixed** — The async executor bean is now properly injected instead of being created twice.
- **Removed `format_sql: true` from default profile** — Only enabled for local development.
- **Redis connection pool configured** — `max-active: 16`, `max-idle: 8`, `min-idle: 2`.
- **S3Client uses default credential chain** — No longer requires explicit static credentials.
- **`RepositoryAuditAspect` guards against batch operations** — `saveAll`/`deleteAll` no longer incorrectly intercepted.
- **`DistrictConverter` fails fast** — Unknown database values throw `IllegalStateException` instead of silently returning null.
- **`PasswordGenerator` guarantees character diversity** — Generated passwords now always contain lowercase, uppercase, digit, and special characters.
- **JJWT bumped 0.11.5 → 0.12.6** — Security fix and API improvements.
- **MapStruct bumped 1.5.5 → 1.6.3** — Latest stable version.

### Performance

- **SecurityService.getCurrentUserId() avoids DB query** — User ID now extracted directly from `UserPrincipal` in the security context.
- **AuthService login optimized** — Three redundant `driverRepository.findByUserId()` calls consolidated into one.

## [Unreleased] — 2026-06-20

### Security Fixes

- **SUPER_ADMIN role hierarchy inverted** — Previously SUPER_ADMIN was restricted to managing only ADMIN users; ADMIN had unrestricted access. Now SUPER_ADMIN has **unrestricted** access to all user management, while ADMIN is **restricted**: cannot create, update, deactivate, or reactivate other ADMIN users. See [USER_API.md](USER_API.md#super-admin).
- **ADMIN users cannot be deactivated** — `DELETE /api/users/{id}` now blocks deactivation of ADMIN users (permanent role).
- **ADMIN cannot manage ADMIN users** — `assertAdminNotManagingAdmin` guard added to `getUserById`, `updateUser`, `deleteUser`, and `activateUser`. Only SUPER_ADMIN can manage ADMIN accounts.

### Features

- **SUPER_ADMIN role with auto-seeder** — New `SUPER_ADMIN` role with restricted scope: can only manage ADMIN users (create, list, update, deactivate) and set the default commission config. Auto-created on first startup via `SUPER_ADMIN_EMAIL`/`SUPER_ADMIN_PASSWORD` env vars. See [USER_API.md](USER_API.md#super-admin) for details.

### Security Fixes

- **Rate limiting bypass on successful login** (`AuthService.login`) — Rate limit is now checked **before** authentication. Previously, providing the correct password after exhausting rate-limit attempts would bypass the rate limiter. Now blocks all attempts (correct or wrong password) once the threshold is reached.
- **Refresh token doesn't check user status** (`AuthService.refreshToken`) — Disabled/locked users can no longer use a refresh token to obtain new access tokens. The check now mirrors `UserDetailsServiceImpl.loadUserByUsername`: rejects if `isActive = false` or `status != ACTIVE`.
- **Driver authorization (own records only)** (`DriverService.updateAvailability`) — Drivers can now only update **their own** availability. Admins can update any driver. Previously any DRIVER-role user could change any other driver's availability.
- **S3 image ownership validation** — `DELETE /api/products/{id}` image removal now validates that the URL belongs to the product before deleting from S3. Prevents deletion of other products' images via URL manipulation.
- **OTP brute-force protection** (`OtpService.verifyOtp`) — After 5 failed verification attempts, the OTP is invalidated and the user must request a new one. Brute-forcing a 6-digit numeric OTP is no longer feasible within the 5-minute validity window.
- **OTP user enumeration** (`AuthService.verifyOtp`) — Unknown emails and wrong OTPs now return the **same** error response (400 "Invalid or expired OTP"). Previously, unknown emails returned 404, allowing attackers to enumerate registered users.
- **Plaintext password logging removed** — Generated passwords are no longer written to application logs in `DriverService`, `UserService`, or `AgentService`. Passwords are only returned in the API response and sent via email.
- **OTP required for backup download** — `GET /api/admin/db/backups/{filename}` now requires an `?otp=...` query parameter, matching the pattern used by `create`, `upload`, and `restore`. Prevents data exfiltration via compromised admin sessions.
- **JWT reset token replay** — Documented behavior: reset tokens expire in 5 minutes and are single-use (subsequent submissions with the same token will fail because the user state has changed).
- **JWT exception handling** (`JwtAuthenticationFilter`) — Malformed/expired JWTs now return 401 instead of 500. Wrapped `extractEmail` in try-catch.
- **Log injection via X-Request-ID** (`CorrelationIdFilter`) — Untrusted `X-Request-ID` headers are now sanitized before being placed in MDC and log output.

### Race Condition / Data Integrity Fixes

- **Duplicate order numbers under concurrency** (`OrderService.createOrder`) — Added `@Retryable` (5 attempts, exponential backoff) for `DataIntegrityViolationException`. Concurrent orders for the same district/source/date now both succeed (one retries with an incremented sequence) instead of one failing with a unique-constraint violation.
- **Narrow @Retryable to duplicate-order-number only** (`OrderService.createOrder`) — `@Retryable` was previously on the entire method, retrying on any `DataIntegrityViolationException`. Narrowed to only catch the specific constraint name `uk_order_number`. Other constraint violations (e.g., FK violations) now fail immediately instead of silently retrying 5 times.
- **Customer `totalOrders` lost updates** (`CustomerService.incrementTotalOrders`) — Replaced read-modify-write with atomic `UPDATE Customer SET totalOrders = totalOrders + 1 WHERE id = :id`. Concurrent order creations can no longer lose increments.
- **Audit log "from X to X" bug** (`OrderService.updateOrderStatus`) — Captures `oldStatus` into a local variable before overwriting it. Audit log now correctly records actual state transitions.
- **NPE when customer has no address** (`OrderService.createOrder`) — Added null check with clear error message.
- **RETURNED orders can be cancelled** (`OrderService.cancelOrder`) — Added `RETURNED` to the blocked-statuses check. Prevents double stock restoration.
- **Category update violates unique constraint** (`CategoryService.updateCategory`) — Switched from `existsByNameIgnoreCaseAndIsActiveTrue` to `existsByNameIgnoreCaseAndIdNot`. Now detects name conflicts with inactive categories before the DB throws.
- **Stock adjustment ignores reserved quantity** (`InventoryService.adjustStock`, `recordOrderStock`, `recordOrderStocks`) — All three methods now reject stock changes that would leave `quantity < reservedQuantity` (preventing negative available stock).

### Logic Bug Fixes

- **Reject mixed-type catalog + per-order discount** (`ProductService.validateDiscount`) — When a product has a catalog discount (`discountType`/`discountValue`) and a per-order max discount cap (`maxDiscountType`/`maxDiscountValue`), both must be the same type (`PERCENTAGE` or `FIXED`). Previously, mixed types (e.g., catalog `PERCENTAGE` + max-cap `FIXED`) could produce inconsistent pricing.

- **PERCENTAGE discount > 100 produces negative price** (`Product.getEffectivePrice`) — Added `.max(BigDecimal.ZERO)` floor. `ProductService.validateDiscount` now rejects PERCENTAGE values > 100 at creation/update time.

> **Discount behavior (verified by tests):** Admins set per-product discount via `POST/PATCH /api/products` with `discountType` (`PERCENTAGE` or `FIXED`) and `discountValue`. Orders automatically use `product.getEffectivePrice()` — the discounted price is applied at order creation. The order's `totalAmount` is the discounted total, and the commission is calculated on this discounted total (see `CommissionCalculationService` line 91). Agent commissions are always based on the final discounted order amount.
>
> **Per-order admin discount (new):** Admins can also set a per-product **max discount** cap (`maxDiscountType` + `maxDiscountValue`) at product creation/update. When placing an order, admin can optionally apply a discount on each `OrderItem` (up to the cap). The discount is optional — if not provided, the product's original price is used. If the discount exceeds the cap, a `400` is returned. The per-order discount is stored on the `OrderItem` for audit, and the commission is calculated on the final discounted `Order.totalAmount`.
- **Driver availability not restored on FAILED→PENDING retry** (`DeliveryService.updateDeliveryStatus`) — When a failed delivery is retried, the previous driver's availability is restored to `AVAILABLE`.
- **Driver assignment doesn't check ON_TRIP** (`DeliveryService.assignDelivery`) — ON_TRIP drivers are now rejected to prevent double-booking.
- **Image sort-order corruption after deletion** (`ProductService.updateProduct`) — In-memory `product.getImages()` collection is updated after DB deletion. New images get correct sort orders with no gaps/overlaps.
- **Commission BigDecimal scale mismatch** (`CommissionCalculationService`) — Changed `divide(BigDecimal.valueOf(100))` to `divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)`. Commission amounts now have scale=2 matching the DB column.

### Performance Optimizations

- **JOIN FETCH + Pageable in-memory pagination** (`OrderRepository`) — Removed `JOIN FETCH o.items` from 4 paginated queries. Page 2 of 20 orders no longer loads 10,000+ orders into memory. SQL now uses `LIMIT/OFFSET`.
- **N+1 in batch image processing** (`ProductService.buildResponsesWithBatchImages`) — Removed per-product `findByProductIdOrderBySortOrder` calls. Was 1+N queries, now 1 query total.
- **Stock entity EAGER fetching** (`Stock` entity, `StockRepository`) — Changed both `@ManyToOne` from EAGER to LAZY. Added `@EntityGraph(attributePaths = {"createdBy"})` to the 2 methods that need it.
- **External calls inside transactions** (`AuthService.changePassword`, `resetPassword`) — Replaced direct `sendPasswordChangedAlert()` calls with `eventPublisher.publishEvent(PasswordChangedEvent)`. A `PasswordChangedEventListener` handles the event with `@TransactionalEventListener(phase = AFTER_COMMIT)`. DB connection is released before email/SMS is sent.
- **Unbounded query pagination** — Added proper pagination to:
  - `DriverService.getDrivers` — accepts `Pageable`, returns `Page<DriverResponse>`
  - `CommissionQueryService.getAllCommissions` — accepts `Pageable`, returns `Page<CommissionResponse>`
  - `DriverRepository.findByAvailability(..., Pageable)` — new paginated method
  - `CommissionRepository.findAllWithDetails(Pageable)` — new paginated method

### Notification System Overhaul

- **Channel-aware template resolution** (`NotificationTemplateService`) — Templates now use a `-email` or `-sms` suffix convention. The service resolves the correct template based on the `NotificationChannel` enum. Backward-compatible with legacy template names.
- **Migrated order notifications** to channel-aware templates — All 8 order notification types (`order-created`, `order-cancelled`, `order-status`, `order-commission-calculated`) now have separate `-email.html` and `-sms.txt` templates for each recipient role (agent + customer).
- **SMS template formatting overhaul** — All SMS templates now use a structured layout with currency formatting (`LKR X,XXX.XX`), line separators, and consistent header/footer. Uses `NumberUtils.formatCurrency()` for locale-independent formatting.
- **HTML-escape template placeholders** (`NotificationTemplateService`) — All `[[placeholder]]` values are now HTML-escaped before template rendering to prevent XSS in email notifications.

### API Hardening

- **Use DiscountType enum in DTOs** (`ProductRequest`, `OrderItemRequest`) — Replaced raw `String` with `DiscountType` enum. Invalid discount values now return `400 Bad Request` instead of being silently ignored.
- **Validate sort field against per-entity allow-list** (`SortableFields`) — Added `SortableFields.validate()` that checks sort fields against a whitelist per entity (`AGENT`, `COMMISSION`, etc.). Prevents DoS via arbitrary sort-by expressions on JPA queries.
- **Cap pagination size and clamp negative page indices** (`PaginationUtils.safePage`, `safeSize`) — Negative page indexes are clamped to 0. Page sizes exceeding per-endpoint max are silently capped instead of returning all records.
- **Add CHECK constraints on money and quantity columns** (Flyway V59) — Added database-level `CHECK` constraints: `commission_amount >= 0`, `total_amount > 0`, `quantity > 0`, `price > 0`, `cost_price > 0`. Prevents data corruption from application bugs reaching the DB.

### Inventory

- **Add @Retryable to recordOrderStocks** (`InventoryService`) — Added `@Retryable` for `OptimisticLockException` on bulk stock recording. Under high concurrency, inventory version conflicts no longer fail the entire order.
- **Skip stock restore when delivery is already FAILED_PERMANENT** (`DeliveryService`) — Prevents double stock restoration on `FAILED_PERMANENT` when stock was already restored by an earlier `FAILED → PENDING` retry cycle.

### New Endpoints

- `GET /api/products/stats` — Returns `{ total, active, lowStock }` for the entire product catalog. Use for dashboard counts that need full-database aggregates.

### Configuration Changes

- **Secrets no longer in `application.yml`** — All sensitive values (DB password, JWT secret, AWS keys, SMTP credentials, SMS credentials) now have empty defaults. They **must** be set via environment variables (or the `.env` file loaded by `spring-dotenv`). The `application.yml` file no longer contains any real secret values.
- New env vars: `DB_URL`, `DB_USERNAME`, `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `MAIL_HOST`, `MAIL_PORT`, `JWT_RESET_SECRET`, `JWT_EXPIRATION`, `JWT_REFRESH_EXPIRATION`, `SERVER_PORT`.

### Known Limitations (Not Fixed)

- In-memory rate limiting (`RateLimitingService`) is JVM-local and does not work in multi-instance deployments without a shared backing store (e.g., Redis). This requires a separate refactor.
- BigDecimal-to-Double conversion in `Agent.totalCommission` / `Agent.totalSales` (financial precision loss). Requires a schema change.
- Leading wildcard LIKE (`%search%`) on customer/product search prevents index usage. Requires full-text search setup.
- `pg_dump` process management could deadlock on large outputs. Mitigated with timeouts; not fully fixed.
- `S3Service.extractKeyFromUrl` uses fragile string replacement. Should use S3 SDK.
