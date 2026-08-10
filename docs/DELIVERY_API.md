# Delivery Module API

Delivery tracking is embedded in the Order entity — each order has delivery fields (status, assigned driver, scheduled date, notes, district).

---

## System Overview

```
┌──────────┐     ┌─────────────┐     ┌─────────────┐
│ CUSTOMER │────▶│    ORDER    │────▶│  DELIVERY   │
│          │     │             │     │             │
│ address  │     │ district    │     │ status      │
│  └───┐   │     │  (auto-set  │     │ assigned_to │
│      │   │     │   from      │     │  └───┐      │
│      ▼   │     │   customer) │     │      │      │
│ district │     │             │     │      ▼      │
└──────────┘     └─────────────┘     │   USER      │
                                     │  (DRIVER)   │
                                     └─────────────┘
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
  ▲                                                     ↘ FAILED ──▶ FAILED_PERMANENT
  └────────────────────── retry ──────────────────────────┘
```

Guards:
- `DELIVERED` and `FAILED_PERMANENT` are terminal — no further updates allowed.
- `FAILED` can transition to `PENDING` (retry) or `FAILED_PERMANENT` (terminal).

### DriverAvailability (manual management by admin)

```
AVAILABLE ──assign delivery──▶ ON_TRIP
     ↘  OFFLINE   (manual by admin)
     ↘  SUSPENDED (manual by admin)
```

Drivers can have multiple active deliveries. Admin manages availability via `PATCH /api/drivers/{id}/availability`.

---

## Validation Guards

| Action | Guards |
|--------|--------|
| Assign Driver to Delivery | delivery status must be `PENDING`, user must have `DRIVER` role, driver must not be `SUSPENDED` |
| Update Delivery Status | current status must not be `DELIVERED` or `FAILED_PERMANENT`; `FAILED` can transition to `PENDING` (retry) or `FAILED_PERMANENT` (terminal) |

---

## Auto-Initialization

- **District**: automatically derived from the customer's address district. Only the supported districts are valid (see [Agent API](AGENT_API.md#supported-districts)).
- **Delivery**: automatically initialized to `PENDING` when the order status changes to `SHIPPED`.
- **Delivered (from delivery)**: when delivery status is updated to `DELIVERED`, order status also updates to `DELIVERED`.
- **Delivered (from order)**: when order status is updated to `DELIVERED`, delivery status also sets to `DELIVERED`.
- **Driver ON_TRIP**: automatically set when a trip is started (driver has active deliveries).
- **Driver availability**: managed by trip system — driver is AVAILABLE when no active trips, ON_TRIP when trip is IN_PROGRESS.
- **deliveryFailureReason**: set when delivery status is updated to `FAILED`; cleared on retry (`FAILED` → `PENDING`).
- **Commission auto-approve**: when delivery status is updated to `DELIVERED`, the associated commission (if any) is auto-approved. Direct orders have no commission and are unaffected.
- **Stock rollback on FAILED_PERMANENT**: when delivery status is updated to `FAILED_PERMANENT`, stock is returned for each order item and the order status is set to `CANCELLED`. `deliveryAssignedTo` and delivery dates are cleared. The associated commission (if any) is cancelled to `CANCELLED` status. `deliveryFailureReason` is preserved for audit.

---

## Trip-Based Assignment (Primary)

Driver assignment is done **via trips**, not directly on orders:

1. Admin creates a trip (`POST /api/trips`) with a driver
2. Admin adds orders to the trip (`PATCH /api/trips/{id}/orders`)
3. When orders are added, `order.deliveryAssignedTo` is set from `trip.driver`
4. Driver starts trip (`PATCH /api/driver/trips/{id}/start`) — trip becomes IN_PROGRESS
5. Driver now sees these orders in their delivery list
6. Driver completes trip — orders no longer visible to driver

See [Trip Management API](TRIP_API.md) for full trip documentation.

---

## Direct Assignment (Deprecated)

The direct assignment endpoint exists for backward compatibility but is not the primary flow:

```
PATCH /api/deliveries/{orderId}/assign
Auth: ADMIN
Body:
{
    "driverId": "uuid"
}
```

Fails if delivery status is not `PENDING`.

---

## Delivery Endpoints

All delivery endpoints return a delivery-specific response (not the full order object):

```json
{
    "orderId": "uuid",
    "orderNumber": "ORD-20260606123456",
    "customerName": "Ranil Wickramasinghe",
    "customerPhone": "+94771234567",
    "district": "Batticaloa",
    "status": "PENDING",
    "driverId": "uuid or null",
    "driverName": "Driver One or null",
    "scheduledDate": "2026-06-10 or null",
    "notes": "Handle with care or null",
    "deliveredAt": "2026-06-10T14:30:00 or null",
    "deliveryFailureReason": "Customer not available or null"
}
```

### List Deliveries

```
GET /api/deliveries?status=PENDING&district=Batticaloa
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

### Update Delivery Status

```
PATCH /api/deliveries/{orderId}/status
Auth: ADMIN, DRIVER
Body:
{
    "status": "IN_TRANSIT"
}
```

Fails if current status is `DELIVERED` or `FAILED_PERMANENT`.

`FAILED` deliveries can transition to:
- `PENDING` — retry (clears failure reason and driver assignment)
- `FAILED_PERMANENT` — mark as terminal failure (keeps the reason)

When setting a failure status, an optional `reason` field can be included:

```json
{
    "status": "FAILED",
    "reason": "Customer not available after 3 attempts"
}
```

The reason is stored in `deliveryFailureReason` on the order, included in customer notifications and admin alerts, and cleared automatically on retry.

---

## Typical Workflow (Trip-Based)

```
1. Order created     → district auto-set from customer address
2. Order → SHIPPED  → deliveryStatus = PENDING
3. Admin creates    → trip with driver assigned
4. Admin adds      → orders to trip
5. Driver starts   → trip (IN_PROGRESS) → sees orders in delivery list
6. Driver delivers → each order: PICKED_UP → IN_TRANSIT → DELIVERED
7. Driver completes → trip (COMPLETED) → orders no longer visible
```

---

## Related Documentation

- [Trip Management API](TRIP_API.md) — Trip CRUD and trip-based order assignment
- [Driver App API](DRIVER_APP_API.md) — Driver trip operations (start, complete, mileage)
