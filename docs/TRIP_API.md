# Trip Management API

The Trip API manages delivery trips — logical groupings of orders assigned to a driver for a specific date range. Trips track driver assignments, mileage, and financial summaries (expected collections, expenses, net amount).

---

## Trip Status Flow

```
PLANNED ──▶ IN_PROGRESS ──▶ COMPLETED
    │             │
    └─── CANCELLED◀┘
```

**Status Rules:**
- `PLANNED`: Initial state. Driver, dates, notes, mileage, and orders can be modified.
- `IN_PROGRESS`: Trip has started. Driver and dates are **locked** — cannot be changed. Orders can still be added/removed.
- `COMPLETED`: Trip finished. All fields and order mutations locked permanently.
- `CANCELLED`: Trip abandoned. All fields and order mutations locked permanently.

---

## Trip Entity

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Unique identifier |
| `name` | String | Trip name (must be globally unique) |
| `driver` | Driver | Assigned driver (optional until start) |
| `startDate` | LocalDate | Trip start date |
| `endDate` | LocalDate | Trip end date |
| `status` | TripStatus | Current status |
| `notes` | String | Admin notes |
| `mileageKm` | BigDecimal | Total distance driven (km) |
| `orders` | List<Order> | Orders assigned to trip |
| `expenses` | List<Expense> | Expenses linked to trip |

**Validation:**
- `endDate` must be on or after `startDate` (create and update).
- `mileageKm` cannot be negative.

---

## Admin Endpoints

### Create Trip

```
POST /api/trips
Auth: ADMIN
Content-Type: application/json
```

```json
{
    "name": "Colombo Morning Route",
    "driverId": "uuid",
    "startDate": "2026-07-30",
    "endDate": "2026-07-30",
    "notes": "Morning deliveries in Colombo area"
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Unique trip name |
| `driverId` | No | Driver UUID (can be assigned later) |
| `startDate` | Yes | Start date |
| `endDate` | Yes | End date (must be >= startDate) |
| `notes` | No | Admin notes |

**Response:** `TripResponse`

---

### List Trips

```
GET /api/trips
GET /api/trips?status=PLANNED
GET /api/trips?driverId=uuid&status=IN_PROGRESS
GET /api/trips?dateFrom=2026-07-01&dateTo=2026-07-31
Auth: ADMIN
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `status` | TripStatus | Filter by status |
| `driverId` | UUID | Filter by driver |
| `dateFrom` | LocalDate | Filter by start date (inclusive) |
| `dateTo` | LocalDate | Filter by end date (inclusive) |

All parameters are optional and combinable (single query).

**Response:** `List<TripResponse>`

---

### List Trips (Paginated)

```
GET /api/trips/paginated?page=0&size=10&sort=createdAt,desc&status=PLANNED&driverId=uuid&dateFrom=2026-07-01&dateTo=2026-07-31
Auth: ADMIN
```

Same optional filters as the list endpoint, plus pagination/sorting. Sortable fields are restricted to those allowed for trips (see `SortableFields`).

**Response:** `PageResponse<TripResponse>`

---

### Get Trip by ID

```
GET /api/trips/{id}
Auth: ADMIN, DRIVER
```

**Driver scoping:** a DRIVER may only fetch their own trip — requests for another driver's trip are rejected with `403`.

**Response:** `TripResponse`

---

### Update Trip (PLANNED only)

```
PUT /api/trips/{id}
Auth: ADMIN
Content-Type: application/json
```

```json
{
    "name": "Updated Trip Name",
    "driverId": "uuid",
    "startDate": "2026-07-31",
    "endDate": "2026-07-31",
    "notes": "Updated notes",
    "mileageKm": 45.5
}
```

**Locked fields (once the trip is not PLANNED):**
- Cannot change `driverId`
- Cannot change `startDate`
- Cannot change `endDate`

**Other rules:**
- `name` must remain globally unique.
- `endDate` must be >= `startDate`.
- `mileageKm` cannot be negative.
- Changing the driver re-syncs `deliveryAssignedTo` on every order attached to the trip.

**Response:** `TripResponse`

---

### Add/Remove Orders

```
PATCH /api/trips/{id}/orders
Auth: ADMIN
Content-Type: application/json
```

```json
{
    "orderIdsToAdd": ["uuid1", "uuid2"],
    "orderIdsToRemove": ["uuid3"]
}
```

**Rules:**
- Cannot modify orders on COMPLETED or CANCELLED trips.
- Trip must have a driver assigned.
- **Add:** the order must be in `SHIPPED` order status and not assigned to a different driver. Orders already on *this* trip are skipped; orders on **another** trip are rejected with `400 IllegalStateException` ("already on trip") — no silent re-assignment.
- Adding sets `order.trip` and `order.deliveryAssignedTo` to the trip's driver.
- **Remove:** unresolved orders (delivery status PENDING/ASSIGNED/PICKED_UP/IN_TRANSIT/FAILED) are fully detached — `trip` and `deliveryAssignedTo` cleared, delivery reset to `PENDING`, failure reason cleared, and a `DeliveryStatusChangedEvent` broadcast. DELIVERED and FAILED_PERMANENT orders only lose the trip reference (their delivery state is preserved).

**Response:** `TripResponse`

---

### Start Trip

```
PATCH /api/trips/{id}/start
Auth: ADMIN
```

**Rules:**
- Trip must be in PLANNED status.
- A driver must be assigned.
- The driver cannot already have another IN_PROGRESS trip.

Every order still in `PENDING` delivery status is transitioned to `ASSIGNED` (each broadcast on `/topic/deliveries`), and the driver's availability is recalculated (ON_TRIP).

**Response:** `TripResponse`

---

### Complete Trip

```
PATCH /api/trips/{id}/complete
Auth: ADMIN
```

**Rules:**
- Trip must be IN_PROGRESS.

Unresolved orders (PENDING/ASSIGNED/PICKED_UP/IN_TRANSIT/FAILED) are detached and reset to `PENDING` (trip/assignee/failure reason cleared) and broadcast on `/topic/deliveries`. DELIVERED and FAILED_PERMANENT orders keep the trip reference. Driver availability is recalculated.

**Response:** `TripResponse`

---

### Cancel Trip

```
PATCH /api/trips/{id}/cancel
Auth: ADMIN
```

**Rules:**
- Refuses to cancel a CANCELLED or COMPLETED trip.

Applies the same order handling as Complete Trip (unresolved orders reset to PENDING and detached), broadcasts trip + delivery events, and recalculates driver availability.

**Response:** `TripResponse`

---

### Order Markers

```
GET /api/trips/{id}/order-markers
Auth: ADMIN, DRIVER
```

Returns lightweight stop markers (order id/number, customer contact, address, delivery/order status, total) for the trip's orders. DRIVER is restricted to their own trip.

```
GET /api/trips/driver/{driverId}/active/order-markers
Auth: ADMIN, DRIVER
```

Returns markers for the driver's current IN_PROGRESS trip. For DRIVER callers the path `driverId` is ignored — the caller's own driver is used.

---

### Delete Trip (PLANNED only)

```
DELETE /api/trips/{id}
Auth: ADMIN
```

**Rules:**
- Only PLANNED trips can be deleted.
- Deletion is blocked (`400 IllegalStateException`) while any order or any expense still references the trip.

---

## Trip Financials (TripResponse)

Each `TripResponse` includes computed financials. **Settlement excludes** orders whose order status is `CANCELLED`, `RETURNED`, or `RETURN_EXCHANGE`, and orders whose delivery status is `FAILED_PERMANENT` — those orders never contribute to the totals below (but still count toward `orderCount`).

| Field | Type | Description |
|-------|------|-------------|
| `orderCount` | int | Total orders on trip (including excluded ones) |
| `paidOrderCount` | int | Saleable orders with `paidAmount >= totalAmount` (> 0) |
| `partialOrderCount` | int | Saleable orders with `0 < paidAmount < totalAmount` |
| `unpaidOrderCount` | int | Saleable orders with `paidAmount = 0` |
| `expectedAmount` | BigDecimal | Sum of `totalAmount` over saleable orders |
| `collectedAmount` | BigDecimal | Sum of `paidAmount` over saleable orders |
| `outstandingAmount` | BigDecimal | expectedAmount - collectedAmount |
| `totalExpenses` | BigDecimal | Sum of APPROVED expenses only |
| `netAmount` | BigDecimal | collectedAmount - totalExpenses |

---

## Expense Categories for Trips

| Category | Amount | Description |
|----------|--------|-------------|
| `PETROL` | > 0 | Fuel expense |
| `PARKING` | > 0 | Parking fees |
| `TOLL` | > 0 | Toll charges |
| `TRAVEL` | > 0 | Travel/food |
| `MAINTENANCE` | > 0 | Vehicle maintenance |
| `OFFICE_SUPPLIES` | > 0 | Office supplies |
| `UTILITIES` | > 0 | Utilities |
| `OTHER` | > 0 | Miscellaneous |

Only expenses in `APPROVED` status count toward `totalExpenses`/`netAmount`.

**Mileage:** tracked directly on `trip.mileageKm` — updated via `PUT /api/trips/{id}` (`mileageKm` body field, ADMIN) or `PATCH /api/driver/trips/{tripId}/mileage?mileageKm=45.5` (DRIVER).

---

## Related Documentation

- [Driver App API](DRIVER_APP_API.md) — Driver trip operations (start, complete, mileage, expenses)
- [Expense API](EXPENSE_API.md) — Expense CRUD and approval workflow
- [Delivery API](DELIVERY_API.md) — Delivery status transitions
- [WebSocket API](WEBSOCKET_API.md) — Real-time `/topic/trips` and `/topic/trip-orders/{driverId}` feeds
