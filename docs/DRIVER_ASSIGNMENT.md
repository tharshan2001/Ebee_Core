# Driver Assignment Rules

> **Source of truth for driver delivery assignment logic.**
> AI agents: read this file before modifying `DeliveryService.java` or `Driver.java`.
> Do NOT change these rules without stakeholder approval.

## Multi-Order Support

- Drivers **CAN** be assigned to multiple orders simultaneously
- There is **no limit** on concurrent active deliveries per driver
- The old 1:1 restriction (ON_TRIP gate) was removed — see History section

## Availability Derivation

Driver availability is **derived from actual active delivery count**, not set directly.

| Availability | Condition |
|---|---|
| `AVAILABLE` | 0 orders with status `ASSIGNED`, `PICKED_UP`, or `IN_TRANSIT` |
| `ON_TRIP` | ≥1 orders with status `ASSIGNED`, `PICKED_UP`, or `IN_TRANSIT` |

The derivation method is `DeliveryService.updateDriverAvailability(driverUserId)`.

## Blocked States

| State | Behavior |
|---|---|
| `SUSPENDED` | Driver **cannot** receive any assignments |
| `OFFLINE` | Driver **cannot** receive any assignments |

These are checked in `DeliveryService.assignDelivery()`.

## When Driver Becomes Available

A driver transitions to `AVAILABLE` only when **ALL** active deliveries are resolved:
- Order delivered (`DELIVERED`)
- Delivery failed (`FAILED`)
- Delivery permanently failed (`FAILED_PERMANENT`)

If a driver has 3 active orders and 1 is delivered, they remain `ON_TRIP` because 2 are still active.

## Order Statuses That Count as "Active"

| Status | Counts as Active? |
|---|---|
| `ASSIGNED` | Yes |
| `PICKED_UP` | Yes |
| `IN_TRANSIT` | Yes |
| `DELIVERED` | No (terminal) |
| `FAILED` | No (terminal) |
| `FAILED_PERMANENT` | No (terminal) |
| `PENDING` | No (not yet assigned) |

## Concurrency Protection

- `Driver` entity has `@Version` field for optimistic locking
- Two concurrent `assignDelivery` calls for the same driver will trigger `ObjectOptimisticLockingFailureException`
- `DeliveryService.assignDelivery()` is `@Transactional`

## Anti-Patterns — DO NOT DO

- ❌ Do NOT set `driver.availability` directly — always use `updateDriverAvailability()`
- ❌ Do NOT add a max concurrent order limit without stakeholder approval
- ❌ Do NOT reintroduce the `ON_TRIP` assignment gate
- ❌ Do NOT change availability derivation to a settable flag
- ❌ Do NOT remove the `@Version` field from Driver
- ❌ Do NOT skip calling `updateDriverAvailability()` after delivery status changes

## History

| Date | Change |
|---|---|
| 2026-06-23 | Multi-order support: removed ON_TRIP gate, added derivation logic |
