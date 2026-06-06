# Delivery Module API

Delivery tracking is embedded in the Order entity — each order has delivery fields (status, assigned driver, scheduled date, notes, district) and an optional Trip assignment.

---

## System Overview

```
┌──────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ CUSTOMER │────▶│    ORDER    │────▶│  DELIVERY   │────▶│    TRIP     │
│          │     │             │     │             │     │             │
│ address  │     │ district    │     │ status      │     │ district    │
│  └───┐   │     │  (auto-set  │     │ assigned_to │     │ trip_date   │
│      │   │     │   from      │     │  └───┐      │     │ driver      │
│      ▼   │     │   customer) │     │      │      │     │  └───┐      │
│ district │     │             │     │      ▼      │     │      ▼      │
└──────────┘     └─────────────┘     │   USER      │     │   USER      │
                                     │  (DRIVER)   │     │  (DRIVER)   │
                                     └──────┬──────┘     └──────┬──────┘
                                            │                   │
                                            └───────────────────┘
                                                  DRIVERS
                                              ┌──────────┐
                                              │ LICENSE  │
                                              │ CONTACT  │
                                              │ AVAIL    │
                                              │  STATUS  │
                                              └──────────┘
```

---

## State Transitions

### DeliveryStatus

```
PENDING ──▶ ASSIGNED ──▶ PICKED_UP ──▶ IN_TRANSIT ──▶ DELIVERED
                                                        ↘ FAILED
```

Guard: `DELIVERED` and `FAILED` are terminal — no further updates allowed.

### TripStatus

```
PLANNED ──▶ IN_PROGRESS ──▶ COMPLETED
                            ↘ CANCELLED
```

Guard: `COMPLETED` and `CANCELLED` are terminal — no further updates allowed.

### DriverAvailability (auto-managed by delivery)

```
AVAILABLE ──assign delivery──▶ ON_TRIP ──deliver──▶ AVAILABLE
     ↘  OFFLINE   (manual)
     ↘  SUSPENDED (manual)
```

Guards:
- Delivery assignment fails if driver is not `AVAILABLE`.
- Trip creation/assignment fails if driver is not `AVAILABLE`.

---

## Validation Guards

| Action | Guards |
|--------|--------|
| Assign Driver to Delivery | delivery status must be `PENDING`, user must have `DRIVER` role, driver must be `AVAILABLE` |
| Update Delivery Status | current status must not be `DELIVERED` or `FAILED` |
| Assign Order to Trip | delivery must be initialized (`deliveryStatus != null`), order not already assigned to a trip, order district must match trip district |
| Create Trip with Driver | driver must be `AVAILABLE` |

---

## Auto-Initialization

- **District**: automatically set from the customer's address district when the order is created.
- **Delivery**: automatically initialized to `PENDING` when the order status changes to `SHIPPED`.
- **Delivered (from delivery)**: when delivery status is updated to `DELIVERED`, order status also updates to `DELIVERED`.
- **Delivered (from order)**: when order status is updated to `DELIVERED`, delivery status also sets to `DELIVERED`.
- **Driver ON_TRIP**: automatically set when a delivery is assigned to the driver.
- **Driver AVAILABLE**: automatically restored when a delivery is marked `DELIVERED`.

---

## Delivery Endpoints

All delivery endpoints return a delivery-specific response (not the full order object):

```json
{
    "orderId": "uuid",
    "orderNumber": "ORD-20260606123456",
    "customerName": "Ranil Wickramasinghe",
    "customerPhone": "+94771234567",
    "district": "Colombo",
    "status": "PENDING",
    "driverId": "uuid or null",
    "driverName": "Driver One or null",
    "scheduledDate": "2026-06-10 or null",
    "notes": "Handle with care or null",
    "deliveredAt": "2026-06-10T14:30:00 or null",
    "tripId": "uuid or null"
}
```

### Assign Driver

```
PATCH /api/deliveries/{orderId}/assign
Auth: ADMIN
Body:
{
    "driverId": "uuid"
}
```

Fails if delivery status is not `PENDING`.

### Update Delivery Status

```
PATCH /api/deliveries/{orderId}/status
Auth: ADMIN, DRIVER
Body:
{
    "status": "IN_TRANSIT"
}
```

Fails if current status is `DELIVERED` or `FAILED`.

### List Deliveries

```
GET /api/deliveries?status=PENDING&district=Colombo&tripId=uuid
Auth: ADMIN, DRIVER
```

All filters optional.

### My Deliveries

```
GET /api/deliveries/mine
Auth: ADMIN, DRIVER
```

Returns deliveries assigned to the current user.

### Delivery Detail

```
GET /api/deliveries/{orderId}
Auth: ADMIN, DRIVER
```

Fails if the order has no delivery.

---

## Trip Endpoints

### Create Trip

```
POST /api/delivery-trips
Auth: ADMIN
Body:
{
    "tripDate": "2026-06-07",
    "district": "Colombo",
    "driverId": "uuid",
    "notes": "Morning run"
}
```

`driverId` is optional.

### List Trips

```
GET /api/delivery-trips?status=PLANNED&district=Colombo
Auth: ADMIN, DRIVER
```

All filters optional.

### Trip Detail

```
GET /api/delivery-trips/{id}
Auth: ADMIN, DRIVER
```

Returns trip info plus list of assigned orders.

### Update Trip Status

```
PATCH /api/delivery-trips/{id}/status?status=IN_PROGRESS
Auth: ADMIN, DRIVER
```

Fails if current status is `COMPLETED` or `CANCELLED`.

### Assign Driver to Trip

```
PATCH /api/delivery-trips/{id}/driver
Auth: ADMIN
Body:
{
    "driverId": "uuid"
}
```

### Assign Order to Trip

```
POST /api/delivery-trips/{tripId}/orders/{orderId}
Auth: ADMIN
```

Fails if order district does not match trip district, or if order is already assigned to a trip.

### Remove Order from Trip

```
DELETE /api/delivery-trips/{tripId}/orders/{orderId}
Auth: ADMIN
```

---

## Typical Workflow

```
1. Order created     → district auto-set from customer address
2. Order → SHIPPED   → deliveryStatus = PENDING
3. Admin views       → "10 pending deliveries in Colombo"
4. Admin creates     → Trip (date=tomorrow, district=Colombo)
5. Admin assigns     → 10 Colombo orders to the trip
6. Driver picks up   → trip IN_PROGRESS, orders PICKED_UP
7. Driver delivers   → each order individually marked DELIVERED
8. All done          → trip COMPLETED
```
