# Commission API Documentation

## Base URL
```
http://localhost:8085/api/commissions
```

## Authentication
All commission endpoints require JWT authentication via Authorization header:
```
Authorization: Bearer <JWT_TOKEN>
```

### Roles
- `ADMIN` - Full access (create, approve, process, pay, reject, read)
- Any authenticated user - Read access (by ID, by agent)

---

## Endpoints

### 1. Calculate Commission
**POST** `/api/commissions/calculate`

Manually calculate and create a commission for an existing order. Commissions are also auto-created when an order is placed via `POST /api/orders` (if the order has an agent).

**Access:** ADMIN only

**Request Headers:**
```
Content-Type: application/json
Authorization: Bearer <JWT_TOKEN>
```

**Request Body:**
```json
{
  "orderId": "UUID"
}
```

**Success Response (201 Created):**
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

**Commission Rate Resolution (priority):**
1. PERFORMANCE config matching agent's `agent_code` → rate from config
2. DEFAULT config with `applies_to = "ALL"` or matching agent's code → rate from config
3. Agent's default `commissionRate` → fallback

**Error Responses:**
- 400: Commission already exists for order
- 401: Unauthorized
- 403: Forbidden

---

### 2. Approve Commission
**PUT** `/api/commissions/{id}/approve`

Marks a commission as APPROVED. Sets `approvedAt` timestamp.

**Access:** ADMIN only

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| id | UUID | Commission ID |

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Commission approved successfully",
  "data": {
    "id": "...",
    "status": "APPROVED",
    "approvedAt": "2026-05-30T11:44:00",
    ...
  }
}
```

---

### 3. Mark as Processing
**PUT** `/api/commissions/{id}/process`

Marks a commission as PROCESSING.

**Access:** ADMIN only

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| id | UUID | Commission ID |

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Commission marked as processing",
  "data": {
    "id": "...",
    "status": "PROCESSING",
    ...
  }
}
```

---

### 4. Mark as Paid
**PUT** `/api/commissions/{id}/pay`

Marks a commission as PAID. Records `paidAt` timestamp and **updates agent accumulators**:
- `agent.totalSales` += `commission.orderAmount`
- `agent.totalCommission` += `commission.commissionAmount`

**Access:** ADMIN only

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| id | UUID | Commission ID |

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Commission marked as paid",
  "data": {
    "id": "...",
    "status": "PAID",
    "paidAt": "2026-05-30T11:44:10",
    ...
  }
}
```

---

### 5. Reject Commission
**PUT** `/api/commissions/{id}/reject?reason={reason}`

Rejects a commission and records the rejection reason as `errorMessage`.

**Access:** ADMIN only

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| id | UUID | Commission ID |

**Query Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| reason | String | Rejection reason |

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Commission rejected",
  "data": {
    "id": "...",
    "status": "REJECTED",
    "errorMessage": "Invalid order",
    ...
  }
}
```

---

### 6. Get Commission by ID
**GET** `/api/commissions/{id}`

Retrieves a specific commission by UUID.

**Access:** Any authenticated user

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| id | UUID | Commission ID |

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Commission retrieved successfully",
  "data": { ... }
}
```

**Error Responses:**
- 404: Commission not found

---

### 7. Get Commissions by Agent
**GET** `/api/commissions/agent/{agentId}`

Retrieves all commissions for a specific agent.

**Access:** Any authenticated user

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| agentId | UUID | Agent ID |

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Commissions retrieved successfully",
  "data": [
    { ... },
    { ... }
  ]
}
```

---

### 8. Get All Commissions (Admin Only)
**GET** `/api/commissions`

Retrieves all commissions in the system.

**Access:** ADMIN only

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Commissions retrieved successfully",
  "data": [ ... ]
}
```

---

### 9. Get Commissions by Status (Admin Only)
**GET** `/api/commissions/status/{status}`

Retrieves all commissions matching a status.

**Access:** ADMIN only

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| status | String | Commission status (CALCULATED, APPROVED, PROCESSING, PAID, REJECTED, CANCELLED) |

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Commissions retrieved successfully",
  "data": [ ... ]
}
```

---

## Commission Config Endpoints — `/api/commission-configs`

### 1. Create Config
**POST** `/api/commission-configs`

**Access:** ADMIN only

**Request Body:**
```json
{
  "name": "Standard Agent Rate",
  "commissionRate": 5.0,
  "type": "DEFAULT",
  "minAmount": 0.0,
  "maxAmount": null,
  "appliesTo": "ALL",
  "isActive": true,
  "effectiveFrom": "2026-01-01T00:00:00",
  "effectiveTo": null
}
```

### 2. Update Config
**PATCH** `/api/commission-configs/{id}`

### 3. Delete Config (Soft)
**DELETE** `/api/commission-configs/{id}`

Sets `isActive = false`.

### 4. Get Config by ID
**GET** `/api/commission-configs/{id}`

### 5. List All Configs
**GET** `/api/commission-configs`

### 6. List Active Configs
**GET** `/api/commission-configs/active`

### 7. By Type
**GET** `/api/commission-configs/type/{type}`

`type`: `DEFAULT` or `PERFORMANCE`

### 8. Active by Type
**GET** `/api/commission-configs/type/{type}/active`

### 9. Shortcuts
**GET** `/api/commission-configs/default`
**GET** `/api/commission-configs/performance`

---

## Data Models

### CommissionCalculateRequest
```json
{
  "orderId": "UUID (required)"
}
```

### CommissionConfigRequest
```json
{
  "name": "String (required)",
  "commissionRate": "Double (required)",
  "type": "CommissionType (DEFAULT|PERFORMANCE)",
  "minAmount": "Double (default: 0)",
  "maxAmount": "Double or null",
  "appliesTo": "String (required, e.g. 'ALL' or agent_code)",
  "isActive": "Boolean (default: true)",
  "effectiveFrom": "LocalDateTime (required)",
  "effectiveTo": "LocalDateTime or null"
}
```

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

---

## Lifecycle State Machine

```
  CALCULATED  (auto-created on order placement, or via /calculate)
      │
      ▼
  APPROVED     (admin calls /approve)
      │
      ▼
  PROCESSING   (admin calls /process)
      │
      ▼
  PAID         (admin calls /pay)  → updates agent.totalSales/totalCommission
      │
      ▼
  CANCELLED    (on order cancel)  → reverts agent totals if was PAID

  Any state ──▶ REJECTED  (admin calls /reject?reason=...)
```

---

## Examples

### Full Lifecycle Flow
```bash
# Login
TOKEN=$(curl -s -X POST http://localhost:8085/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@ebee.com","password":"admin123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# Create order (auto-creates commission)
ORDER=$(curl -s -X POST http://localhost:8085/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "81c662e0-80e0-450d-b492-adc1c15103c7",
    "items": [{"productId": "edeaad97-16b6-4328-856b-1919a88ac383", "quantity": 2}]
  }')

# Get the commission ID
CID=$(curl -s http://localhost:8085/api/commissions \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][-1]['id'])")

# Approve → Process → Pay
curl -X PUT "http://localhost:8085/api/commissions/$CID/approve" \
  -H "Authorization: Bearer $TOKEN"

curl -X PUT "http://localhost:8085/api/commissions/$CID/process" \
  -H "Authorization: Bearer $TOKEN"

curl -X PUT "http://localhost:8085/api/commissions/$CID/pay" \
  -H "Authorization: Bearer $TOKEN"

# Check commission status
curl http://localhost:8085/api/commissions/$CID \
  -H "Authorization: Bearer $TOKEN"
```

---

## Error Handling

All errors follow the standard ApiResponse format:
```json
{
  "success": false,
  "message": "Error description",
  "data": null
}
```

**Common Error Codes:**
| Status Code | Description |
|-------------|-------------|
| 400 | Bad Request / Illegal state |
| 401 | Unauthorized - Invalid/missing token |
| 403 | Forbidden - Insufficient permissions |
| 404 | Not Found - Resource not found |
| 500 | Internal Server Error |
