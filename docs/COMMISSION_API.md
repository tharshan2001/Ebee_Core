# Commission API Reference

Two categories of endpoints:
- **Commissions** (`/api/commissions`) — the commission lifecycle (calculate → approve → process → pay)
- **Commission Configs** (`/api/commission-configs`) — rate configuration (DEFAULT global, PERFORMANCE per-agent)

---

# Commissions

**Base URL:** `http://localhost:8085/api/commissions`

## Authentication

All endpoints require JWT:
```
Authorization: Bearer <JWT_TOKEN>
```

**Roles:**
- `ADMIN` — full access (create, approve, process, pay, reject, read)
- Any authenticated user — read access (by ID, by agent)

---

## Commission Rate Resolution

When a commission is calculated (auto on order creation, or manual via `/calculate`), the rate is resolved based on the agent's `commissionType`:

| Agent Type | Resolution |
|---|---|
| `DEFAULT` | Uses the global `DEFAULT` commission config rate. Falls back to agent's own `commissionRate` if no default config exists. |
| `PERFORMANCE` | Config lookup is skipped entirely. Agent uses their own `commissionRate` directly. |

---

## Endpoints

### Calculate Commission

**POST** `/api/commissions/calculate`

Calculates commission for an existing order. Commissions are also auto-created when an order is placed (if the order has an agent).

**Access:** ADMIN only

**Request:**
```json
{
  "orderId": "UUID"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Commission calculated successfully",
  "data": {
    "id": "08eb3353-cef0-438a-b782-6c5304c393d7",
    "agentId": "81c662e0-80e0-450d-b492-adc1c15103c7",
    "agentName": "Anton Abitharshan",
    "orderId": "8169f41f-4834-46d2-8d30-9fbfb103466a",
    "orderNumber": "ORD-20260530114354",
    "orderAmount": 180.00,
    "commissionRate": 5.00,
    "commissionAmount": 9.00,
    "status": "CALCULATED",
    "calculatedAt": "2026-05-30T11:43:54",
    "createdAt": "2026-05-30T11:43:54"
  }
}
```

**Errors:**
- 400: Commission already exists for order / Order has no agent

---

### Lifecycle Transitions

| Action | Endpoint | Description |
|--------|----------|-------------|
| Approve | **PUT** `/api/commissions/{id}/approve` | Marks as `APPROVED`, sets `approvedAt` |
| Process | **PUT** `/api/commissions/{id}/process` | Marks as `PROCESSING` |
| Pay | **PUT** `/api/commissions/{id}/pay` | Marks as `PAID`, sets `paidAt`, updates agent totals |
| Reject | **PUT** `/api/commissions/{id}/reject?reason=...` | Rejects with reason (any state) |

All transitions require ADMIN role.

**State Machine:**
```
CALCULATED  (auto on order creation, or via /calculate)
    │
    ▼
APPROVED    (admin calls /approve)
    │
    ▼
PROCESSING  (admin calls /process)
    │
    ▼
PAID        (admin calls /pay)  →  updates agent totalSales / totalCommission
    │
    ▼
CANCELLED   (on order cancel)   →  reverts agent totals if was PAID

Any state ──→ REJECTED (admin calls /reject?reason=...)
```

---

### Read Endpoints

| Endpoint | Access | Description |
|----------|--------|-------------|
| **GET** `/api/commissions/{id}` | Any authenticated | Get by ID |
| **GET** `/api/commissions/agent/{agentId}` | Any authenticated | Get by agent |
| **GET** `/api/commissions` | ADMIN only | Get all |
| **GET** `/api/commissions/status/{status}` | ADMIN only | Filter by status |

Status values: `CALCULATED`, `APPROVED`, `PROCESSING`, `PAID`, `REJECTED`, `CANCELLED`.

---

# Commission Configs

**Base URL:** `http://localhost:8085/api/commission-configs`

## Overview

There is exactly **one** active `DEFAULT` config at any time. This is the global rate applied to all agents with `commissionType: DEFAULT`.

- Use `PUT /api/commission-configs/default` to create or update.
- `POST` with `type: DEFAULT` is **blocked** if one already exists.

To give an agent a custom rate, set their `commissionType` to `PERFORMANCE` via PATCH update and set their `commissionRate` directly.

---

## Endpoints

### Create Config

**POST** `/api/commission-configs`

**Access:** ADMIN only

```json
{
  "name": "Global Default Rate",
  "commissionRate": 7.5,
  "type": "DEFAULT",
  "appliesTo": "ALL",
  "isActive": true
}
```

> Creating a second `DEFAULT` config throws an error. Use `PUT /default` instead.

---

### Upsert Default Config

**PUT** `/api/commission-configs/default`

Creates or updates the single default config. If no default exists, a new one is created. If one exists, it is updated.

```bash
curl -X PUT http://localhost:8085/api/commission-configs/default \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Global Default",
    "commissionRate": 7.5,
    "appliesTo": "ALL"
  }'
```

---

### Shortcut Endpoint

| Endpoint | Description |
|----------|-------------|
| **GET** `/api/commission-configs/default` | Returns the single default config object |

### Standard CRUD Endpoints

| Endpoint | Description |
|----------|-------------|
| **GET** `/api/commission-configs` | List all configs |
| **GET** `/api/commission-configs/active` | List active configs only |
| **GET** `/api/commission-configs/{id}` | Get by ID |
| **PATCH** `/api/commission-configs/{id}` | Update config |
| **DELETE** `/api/commission-configs/{id}` | Soft-delete (sets `isActive = false`) |
| **GET** `/api/commission-configs/type/{type}` | Filter by type (only `DEFAULT`) |
| **GET** `/api/commission-configs/type/{type}/active` | Active configs by type |

---

## Config Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| name | string | yes | Display name |
| commissionRate | number | yes | Rate percentage (e.g., 7.5) |
| type | string | yes | `DEFAULT` |
| appliesTo | string | yes | `"ALL"` |
| minAmount | number | no | Minimum order amount threshold |
| maxAmount | number | no | Maximum order amount threshold |
| isActive | boolean | no | Defaults to `true` |
| effectiveFrom | datetime | no | Start of validity |
| effectiveTo | datetime | no | End of validity |

---

# Data Models

### CommissionResponse

```json
{
  "id": "UUID",
  "agentId": "UUID",
  "agentName": "String",
  "orderId": "UUID",
  "orderNumber": "String",
  "orderAmount": "BigDecimal",
  "commissionRate": "BigDecimal",
  "commissionAmount": "BigDecimal",
  "status": "CommissionStatus",
  "calculatedAt": "LocalDateTime or null",
  "approvedAt": "LocalDateTime or null",
  "paidAt": "LocalDateTime or null",
  "notes": "String or null",
  "errorMessage": "String or null",
  "createdAt": "LocalDateTime",
  "updatedAt": "LocalDateTime"
}
```

### CommissionConfigResponse

```json
{
  "id": "UUID",
  "name": "String",
  "commissionRate": "Double",
  "type": "CommissionType",
  "minAmount": "Double",
  "maxAmount": "Double or null",
  "appliesTo": "String",
  "isActive": "Boolean",
  "effectiveFrom": "LocalDateTime",
  "effectiveTo": "LocalDateTime or null",
  "createdAt": "LocalDateTime",
  "updatedAt": "LocalDateTime"
}
```
