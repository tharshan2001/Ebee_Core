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
- **Driver ON_TRIP**: automatically set when a delivery is assigned to the driver.
- **Driver availability**: managed manually by admin via `PATCH /api/drivers/{id}/availability`. A driver can have multiple active deliveries.
- **deliveryFailureReason**: set when delivery status is updated to `FAILED`; cleared on retry (`FAILED` → `PENDING`).
- **Commission auto-approve**: when delivery status is updated to `DELIVERED`, the associated commission (if any) is auto-approved. Direct orders have no commission and are unaffected.
- **Stock rollback on FAILED_PERMANENT**: when delivery status is updated to `FAILED_PERMANENT`, stock is returned for each order item and the order is reset to `PENDING` status with `deliveryAssignedTo` and delivery dates cleared. The associated commission (if any) is cancelled to `CANCELLED` status. `deliveryFailureReason` is preserved for audit.

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

## Typical Workflow

```
1. Order created     → district auto-set from customer address
2. Order → SHIPPED   → deliveryStatus = PENDING
3. Admin views       → "10 pending deliveries in Batticaloa"
4. Admin assigns     → driver to each delivery
5. Driver delivers   → each order marked PICKED_UP → IN_TRANSIT → DELIVERED
```
