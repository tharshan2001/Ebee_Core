# Trip Management API

The Trip API manages delivery trips — logical groupings of orders assigned to a driver for a specific date range. Trips track driver assignments, mileage, and financial summaries (expected collections, expenses, net amount).

---

## Trip Status Flow

```
PLANNING ──▶ IN_PROGRESS ──▶ COMPLETED
     │              │
     └──── CANCELLED┘
```

**Status Rules:**
- `PLANNING`: Initial state. Driver, dates, notes, and orders can be modified.
- `IN_PROGRESS`: Trip has started. Driver and dates are **locked** — cannot be changed. Orders can still be updated.
- `COMPLETED`: Trip finished. All fields locked permanently.
- `CANCELLED`: Trip abandoned. All fields locked permanently.

---

## Trip Entity

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Unique identifier |
| `name` | String | Trip name (must be globally unique) |
| `driver` | Driver | Assigned driver |
| `startDate` | LocalDate | Trip start date |
| `endDate` | LocalDate | Trip end date |
| `status` | TripStatus | Current status |
| `notes` | String | Admin notes |
| `mileageKm` | BigDecimal | Total distance driven (km) |
| `orders` | List<Order> | Orders assigned to trip |
| `expenses` | List<Expense> | Expenses linked to trip |

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
| `driverId` | Yes | Driver UUID |
| `startDate` | Yes | Start date |
| `endDate` | Yes | End date |
| `notes` | No | Admin notes |

**Response:** `TripResponse`

---

### List Trips

```
GET /api/trips
GET /api/trips?status=PLANNING
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

**Response:** `List<TripResponse>`

---

### Get Trip by ID

```
GET /api/trips/{id}
Auth: ADMIN, DRIVER
```

**Response:** `TripResponse`

---

### Update Trip (PLANNING only)

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
    "notes": "Updated notes"
}
```

**Locked fields (after PLANNING):**
- Cannot change `driverId` once trip is not PLANNING
- Cannot change `startDate` once trip is not PLANNING
- Cannot change `endDate` once trip is not PLANNING

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
- Cannot modify orders on COMPLETED or CANCELLED trips
- Cannot add an order already assigned to another trip
- Adding orders sets `order.deliveryAssignedTo` from trip.driver
- Removing orders sets `order.trip = null` and `order.deliveryAssignedTo = null`

**Response:** `TripResponse`

---

### Cancel Trip

```
PATCH /api/trips/{id}/cancel
Auth: ADMIN
```

Sets status to CANCELLED, orphan orders back to PENDING delivery status, removes trip and driver references from orders.

**Cannot cancel COMPLETED trips.**

**Response:** `TripResponse`

---

### Delete Trip (PLANNING only)

```
DELETE /api/trips/{id}
Auth: ADMIN
```

Only PLANNING trips can be deleted.

---

## Trip Financials (TripResponse)

Each `TripResponse` includes computed financials:

| Field | Type | Description |
|-------|------|-------------|
| `orderCount` | int | Total orders on trip |
| `paidOrderCount` | int | Orders with paymentStatus = PAID |
| `partialOrderCount` | int | Orders with paymentStatus = PARTIAL |
| `unpaidOrderCount` | int | Orders with paymentStatus = UNPAID |
| `expectedAmount` | BigDecimal | Sum of totalAmount for all orders |
| `collectedAmount` | BigDecimal | Sum of paidAmount for all orders |
| `outstandingAmount` | BigDecimal | expectedAmount - collectedAmount |
| `netAmount` | BigDecimal | collectedAmount - totalExpenses |
| `totalExpenses` | BigDecimal | Sum of all approved expenses |
| `petrolCost` | BigDecimal | Sum of PETROL expenses |
| `parkingCost` | BigDecimal | Sum of PARKING expenses |
| `tollCost` | BigDecimal | Sum of TOLL expenses |
| `travelCost` | BigDecimal | Sum of TRAVEL expenses |
| `maintenanceCost` | BigDecimal | Sum of MAINTENANCE expenses |
| `otherCost` | BigDecimal | Sum of OTHER expenses |

---

## Expense Categories for Trips

| Category | Amount | Distance | Description |
|----------|--------|----------|-------------|
| `PETROL` | > 0 | null | Fuel expense |
| `MILEAGE` | 0 | > 0 | Mileage log (no cost) |
| `PARKING` | > 0 | null | Parking fees |
| `TOLL` | > 0 | null | Toll charges |
| `TRAVEL` | > 0 | null | Travel/food |
| `MAINTENANCE` | > 0 | null | Vehicle maintenance |
| `OTHER` | > 0 | null | Miscellaneous |

**Mileage vs PETROL:**
- Mileage is tracked on `trip.mileageKm` field directly
- PETROL is a real expense with `amount > 0`
- MILEAGE entries have `amount = 0` and `distanceKm > 0`

---

## Related Documentation

- [Driver App API](DRIVER_APP_API.md) — Driver trip operations (start, complete, mileage, expenses)
- [Expense API](EXPENSE_API.md) — Expense CRUD and approval workflow
- [Delivery API](DELIVERY_API.md) — Delivery status transitions
