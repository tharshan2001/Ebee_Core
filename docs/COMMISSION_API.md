# Commission API Reference

Two categories of endpoints:
- **Commissions** (`/api/commissions`) — the commission lifecycle (calculate → auto-approve → auto-process after 10 days → manual pay)
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
- `ADMIN` — full access (create, process, pay, reject, read, batch pay)
- All commission endpoints are ADMIN-only

---

## Commission Rate Resolution

When a commission is calculated (auto on order creation, or manual via `/calculate`), the rate is resolved based on the agent's `commissionType`:

| Agent Type | Resolution |
|---|---|
| `DEFAULT` | Uses the global `DEFAULT` commission config rate. Falls back to agent's own `commissionRate` if no default config exists. |
| `PERFORMANCE` | Config lookup is skipped entirely. Agent uses their own `commissionRate` directly. |

---

## Auto-Lifecycle (no manual approve needed)

| Step | Trigger | Description |
|------|---------|-------------|
| `CALCULATED` | Order placed (or manual `/calculate`) | Commission created with calculated amount |
| `APPROVED` | Order delivered (`DELIVERED` status) | Auto-approved via delivery system — no admin action needed |
| `PROCESSING` | 10 days after approval | Daily scheduler (2am) auto-transitions commissions that have been `APPROVED` for 10+ days |
| `PAID` | Admin calls `/pay` or `/pay-batch` | Manual step — admin pays one-by-one or in batch |

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
    "agentCode": "COL-001",
    "agentName": "Anton Abitharshan",
    "agentTotalSales": 180.00,
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
| Process | **PUT** `/api/commissions/{id}/process` | Manually marks as `PROCESSING` (admin override; normal flow is auto after 10 days) |
| Pay | **PUT** `/api/commissions/{id}/pay` | Marks a single commission as `PAID`, sets `paidAt`, updates agent totals |
| Reject | **PUT** `/api/commissions/{id}/reject?reason=...` | Rejects with reason (any state) |

All transitions require ADMIN role.

> The `approve` step has been removed as a manual action. Commissions are auto-approved when the order is delivered. After 10 days in `APPROVED` status, they are auto-transitioned to `PROCESSING` by a daily scheduler.

**State Machine:**
```
CALCULATED  (auto on order creation, or via /calculate)
    │
 auto on DELIVERED
    │
    ▼
APPROVED
    │
 after 10 days (daily scheduler at 2am)
    │
    ▼
PROCESSING  (admin marks as /pay or /pay-batch)
    │
    ▼
PAID        → updates agent totalSales / totalCommission

Any state ──→ CANCELLED (on cancelled order or failed_permanent) → reverts agent totals if was PAID
Any state ──→ REJECTED (admin calls /reject?reason=...)
```

---

### Batch Payment

#### GET /api/commissions/pending-payment

Returns a summary of `PROCESSING` commissions, sorted FIFO by `approvedAt` (oldest first). Use this to see the total amount available to pay and the individual commissions that can be paid.

**Access:** ADMIN only

**Query Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `agentId` | `UUID` | Yes | Filter by agent. Required — payments are processed per agent. |

**Example:**
```bash
curl -s "http://localhost:8085/api/commissions/pending-payment?agentId=05cef7aa-ac7c-4498-bd7b-dc51d9acddd5" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response:**
```json
{
  "success": true,
  "message": "Pending payment summary retrieved successfully",
  "data": {
    "totalAvailable": 50000.00,
    "processingCount": 8,
    "commissions": [
      {
        "id": "uuid",
        "orderNumber": "ORD-001",
        "commissionAmount": 7500.00,
        "status": "PROCESSING",
        "approvedAt": "2026-06-01T10:00:00",
        "...": "..."
      }
    ]
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `totalAvailable` | `BigDecimal` | Sum of all `PROCESSING` commission amounts for the agent |
| `processingCount` | `int` | Number of commissions currently in `PROCESSING` status |
| `commissions` | `CommissionResponse[]` | Full list of `PROCESSING` commissions (oldest `approvedAt` first) |

---

#### POST /api/commissions/pay-batch

Marks one or more specific commissions as `PAID`. Only commissions in `PROCESSING` status can be paid. The request specifies which commission IDs to pay — admin selects from the list returned by `GET /pending-payment`.

**Access:** ADMIN only

**Request:**
```json
{
  "commissionIds": ["uuid-1", "uuid-2", "uuid-3"]
}
```

**Response:**
```json
{
  "success": true,
  "message": "Batch payment of LKR 30000.00 completed. 3 commission(s) marked as paid.",
  "data": {
    "totalPaid": 30000.00,
    "commissionsPaid": 3,
    "remainingAvailable": 20000.00,
    "paidCommissions": [
      { "id": "uuid-1", "orderNumber": "ORD-001", "commissionAmount": 10000.00, "status": "PAID", "..." },
      { "id": "uuid-2", "orderNumber": "ORD-002", "commissionAmount": 12000.00, "status": "PAID", "..." },
      { "id": "uuid-3", "orderNumber": "ORD-003", "commissionAmount": 8000.00, "status": "PAID", "..." }
    ]
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `totalPaid` | `BigDecimal` | Sum of commission amounts that were paid |
| `commissionsPaid` | `int` | Number of commissions processed |
| `remainingAvailable` | `BigDecimal` | Sum of remaining `PROCESSING` commissions after this batch (updated from DB) |
| `paidCommissions` | `CommissionResponse[]` | List of commissions that were just paid |

**Error (commission not in PROCESSING status):**
```json
{
  "success": false,
  "message": "Commission <id> for order ORD-001 is not in PROCESSING status (current: APPROVED)",
  "data": null
}
```

**Typical flow:**
1. Admin calls `GET /pending-payment` → sees `totalAvailable: 50000` and the list of 8 processing commissions
2. Admin selects which commissions to pay (e.g., 3 commissions totalling 30000)
3. Admin calls `POST /pay-batch` with those 3 IDs
4. Response shows `totalPaid: 30000`, `remainingAvailable: 20000`, and the 3 paid commissions
5. Agent receives a "Paid" notification for each commission

---

### Read Endpoints

| Endpoint | Access | Description |
|----------|--------|-------------|
| **GET** `/api/commissions/{id}` | ADMIN only | Get by ID |
| **GET** `/api/commissions/agent/{agentId}` | ADMIN only | Get by agent |
| **GET** `/api/commissions` | ADMIN only | Get all (paginated) |
| **GET** `/api/commissions/status/{status}` | ADMIN only | Filter by status |

#### GET /api/commissions (Paginated)

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | Integer | `0` | Zero-based page index |
| `size` | Integer | `20` | Page size (max 100) |
| `sort` | String | `calculatedAt,desc` | Format: `field,direction` |

**Response (200):** `PageResponse<CommissionResponse>` wrapper (see `PageResponse` shape in README).

```bash
curl -s "http://localhost:8085/api/commissions?page=0&size=20&sort=calculatedAt,desc" \
  -H "Authorization: Bearer <TOKEN>"
```

Status values: `CALCULATED`, `APPROVED`, `PROCESSING`, `PAID`, `REJECTED`, `CANCELLED`.

> `agentTotalSales` is computed dynamically — the sum of all non-cancelled/non-rejected order amounts for that agent. It reflects total sales regardless of payment status.
>
> **Commission amount precision:** Commission amounts are `BigDecimal` with `scale=2` and `RoundingMode.HALF_UP` (e.g. `33.33 * 10 / 100 = 3.33`). The DB column is `precision=12, scale=2`. No silent truncation occurs.

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
  "agentCode": "String",
  "agentName": "String",
  "agentTotalSales": "BigDecimal",
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
