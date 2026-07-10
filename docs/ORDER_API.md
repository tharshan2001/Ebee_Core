# Order API Documentation

## Base URL
```
http://localhost:8085/api/orders
```

## Authentication
All order endpoints require JWT authentication via Authorization header:
```
Authorization: Bearer <JWT_TOKEN>
```

### Roles
- `ADMIN` - Full access (create, read, update, cancel orders)
- `AGENT` - Limited access (create orders, read own orders)

---

## Order Source Types

The system supports two types of orders:

| Order Source | Description | Who Can Create | Commission |
|--------------|-------------|----------------|------------|
| `AGENT` | Order linked to an agent | Admin (with agentCode) or Agent (self) | Yes, auto-calculated |
| `DIRECT` | Order without an agent | Admin only (omit agentCode) | No commission |

---

## Payment Methods

The system supports the following payment methods:

| Payment Method | Description | Auto-Payment Status on Create |
|----------------|-------------|-------------------------------|
| `CASH` | Cash payment | `PAID` — paidAmount = totalAmount |
| `COD` | Cash on delivery | `UNPAID` — outstandingBalance = totalAmount |
| `BANK_TRANSFER` | Bank transfer | `PAID` — paidAmount = totalAmount |
| `CREDIT` | Credit (full or partial) | `PAID` / `PARTIALLY_PAID` / `UNPAID` — depends on paidAmount provided |
| `CARD_PAYMENT` | Card payment | `PAID` — paidAmount = totalAmount |

### Credit / Partial Payment

When paying by credit:
- If no `paidAmount` is provided → `UNPAID`
- If `paidAmount < totalAmount` → `PARTIALLY_PAID`, `outstandingBalance` tracks remaining
- If `paidAmount >= totalAmount` → `PAID`

Additional payments can be recorded via the `POST /api/orders/{id}/payment` endpoint.

---

## Endpoints

### 1. Create Order
**POST** `/api/orders`

Creates a new order with one or more products.

**Access:** ADMIN, AGENT

**Request Headers:**
```
Content-Type: application/json
Authorization: Bearer <JWT_TOKEN>
```

#### Scenario A: Agent creates their own order (Agent login)
The `agentCode` is **automatically resolved from the JWT token**.

```json
{
  "customerPhoneNumber": "0111111111",
  "paymentMethod": "CASH",
  "notes": "Optional order notes",
  "items": [
    {
      "productItemCode": "PA-001",
      "quantity": 2
    }
  ]
}
```

#### Scenario B: Admin places a direct order (No agent)
Admin omits `agentCode`. Order is created as `orderSource: "DIRECT"`.

```json
{
  "customerPhoneNumber": "0111111111",
  "paymentMethod": "CARD_PAYMENT",
  "notes": "Walk-in customer",
  "items": [
    {
      "productItemCode": "PA-001",
      "quantity": 2
    }
  ]
}
```

#### Scenario C: Admin places order on behalf of an agent
Admin includes `agentCode` to create an order for a specific agent.

```json
{
  "agentCode": "TEST-002",
  "customerPhoneNumber": "0111111111",
  "paymentMethod": "CASH",
  "notes": "Order for agent TEST-002",
  "items": [
    {
      "productItemCode": "PA-001",
      "quantity": 2
    }
  ]
}
```

#### Scenario D: Credit order with partial payment
Admin creates a credit order with a partial upfront payment. The order will be `PARTIALLY_PAID` with the remaining balance tracked.

```json
{
  "agentCode": "TEST-002",
  "customerPhoneNumber": "0111111111",
  "paymentMethod": "CREDIT",
  "paidAmount": 5000.00,
  "notes": "Half paid via credit",
  "items": [
    {
      "productItemCode": "PA-001",
      "quantity": 10
    }
  ]
}
```

**Validation:**
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| customerPhoneNumber | String | Yes | Must match an existing customer's phone number (e.g., `0111111111`) |
| agentCode | String | No (admin only) | For admin: set to create order for a specific agent. For agent: **ignored** — resolved automatically from JWT. Omit for **direct order**. |
| paymentMethod | String | Yes | `CASH`, `COD`, `BANK_TRANSFER`, `CREDIT`, or `CARD_PAYMENT` |
| notes | String | No | Max 1000 characters |
| items | Array | Yes | At least 1 item |
| items[].productItemCode | String | Yes | Must match an active product's item code (e.g., `PA-001`) |
| items[].quantity | Integer | Yes | Positive integer |
| items[].discountType | String | No (admin only) | `PERCENTAGE` or `FIXED` — per-order discount. Ignored for AGENT-created orders. |
| items[].discountValue | BigDecimal | No | Required if `discountType` is set. Capped by the product's `maxDiscountValue`. |
| paidAmount | BigDecimal | No | For `CREDIT` payment — set to record partial/full payment at order creation. Ignored for non-credit methods. |

**Success Response (201 Created):**
```json
{
  "success": true,
  "message": "Order created successfully",
  "data": {
    "id": "fbd1b509-09a4-4848-92aa-8ad00360b9f3",
    "orderNumber": "TM-D-20260614-001",
    "agentId": null,
    "agentCode": null,
    "agentName": "Direct Order",
    "customerId": "555ec919-b289-4e31-92e7-bf5b0594041b",
    "customerName": "Arun Kumar",
    "customerPhone": "0111111111",
    "totalAmount": 120.00,
    "status": "PENDING",
    "paymentMethod": "CASH",
    "paymentStatus": "PAID",
    "paidAmount": 120.00,
    "paidAt": "2026-06-14T22:23:36.573066",
    "outstandingBalance": 0.00,
    "notes": "Direct order test",
    "createdById": "5b718629-785e-4adf-a475-01e7c5957670",
    "createdByName": "Admin User",
    "createdAt": "2026-06-14T22:23:36.573066",
    "updatedAt": "2026-06-14T22:23:36.573071",
    "items": [
      {
        "id": "ecb7f181-37a9-4a56-a8ad-f48c41b25ae6",
        "productId": "edeaad97-16b6-4328-856b-1919a88ac383",
        "productName": "Updated A",
        "productItemCode": "PA-001",
        "quantity": 2,
        "unitPrice": 60.00,
        "originalUnitPrice": 60.00,
        "subtotal": 120.00,
        "discountType": null,
        "discountValue": null
      }
    ],
    "district": "Trincomalee",
    "deliveryStatus": null,
    "deliveryAssignedToId": null,
    "deliveryAssignedToName": null,
    "deliveryScheduledDate": null,
    "deliveryNotes": null,
    "orderSource": "DIRECT"
  }
}
```

**Key Differences by Order Source:**

| Field | Direct Order | Agent Order |
|-------|-------------|-------------|
| `agentId` | `null` | Agent's UUID |
| `agentCode` | `null` | Agent's code (e.g., `TEST-002`) |
| `agentName` | `"Direct Order"` | Agent's full name |
| `orderSource` | `"DIRECT"` | `"AGENT"` |
| `orderNumber` | Prefix: `TM-D-...` | Prefix: `TM-A-...` |
| Commission | No | Yes, auto-calculated |

**Pricing Note:** The `unitPrice` in each order item reflects the product's **effective price** (after any active discount, plus any per-order admin discount). If the product has `discountType: "PERCENTAGE"` with `discountValue: 10`, the `unitPrice` will be `price * 0.9`. Agents see this discounted price automatically and cannot override it.

**Per-order admin discount:** Admins can optionally apply a discount on each `OrderItem` by supplying `discountType` and `discountValue`. This is capped by the product's `maxDiscountType` / `maxDiscountValue`. The `originalUnitPrice` field captures the price before any per-order discount. When no per-order discount is applied, `originalUnitPrice` equals `unitPrice`.

**Error Responses:**
- 400: Agent not active / Product not active / Insufficient stock
- 401: Unauthorized (invalid/missing token)
- 403: Forbidden (role not authorized)
- 404: Agent not found / Customer not found / Product not found

> **Order Number Concurrency:** Order numbers are unique per (district, source, date, sequence). The `order_number` column has a `UNIQUE` constraint at the database level. Concurrent requests for the same district/source/date are handled via `@Retryable` (5 attempts, exponential backoff: 50ms × 2^n). If all retries fail, the client receives `500 Unable to generate unique order number. Please try again.`
>
> **Order Number Format:** `TM-{D|A}-YYYYMMDD-NNN` where:
> - `D` = direct order, `A` = agent order
> - `YYYYMMDD` = creation date (server local time)
> - `NNN` = zero-padded daily sequence (resets each day per district+source)

---

### 2. Get Order by ID
**GET** `/api/orders/{id}`

Retrieves a specific order by its UUID.

**Access:** ADMIN, AGENT

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| id | UUID | Order ID (e.g., `fbd1b509-09a4-4848-92aa-8ad00360b9f3`) |

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Order retrieved successfully",
  "data": { ... }
}
```

**Error Responses:**
- 404: Order not found
- 401: Unauthorized

---

### 3. Get Order by Order Number
**GET** `/api/orders/number/{orderNumber}`

Retrieves a specific order by its order number.

**Access:** ADMIN, AGENT

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| orderNumber | String | Order number (e.g., `TM-D-20260614-001`) |

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Order retrieved successfully",
  "data": { ... }
}
```

---

### 4. Get Orders by Agent (Admin Only)
**GET** `/api/orders/agent/{agentId}`

Retrieves all orders for a specific agent.

**Access:** ADMIN only

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| agentId | UUID | Agent ID (e.g., `adf3427f-a16a-4683-97f9-ba2863ede45e`) |

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Orders retrieved successfully",
  "data": [
    { ... },
    { ... }
  ]
}
```

---

### 5. Get Orders by Customer

**GET** `/api/orders/customer/{customerId}`

Retrieves all orders for a specific customer. Optionally filter by payment status.

**Access:** ADMIN, AGENT

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| customerId | UUID | Customer ID (e.g., `555ec919-b289-4e31-92e7-bf5b0594041b`) |

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| paymentStatus | PaymentStatus | No | Filter by payment status: `UNPAID`, `PARTIALLY_PAID`, `PAID`, `REFUNDED` |

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Orders retrieved successfully",
  "data": [
    {
      "id": "fbd1b509-09a4-4848-92aa-8ad00360b9f3",
      "orderNumber": "TM-D-20260614-001",
      "agentCode": null,
      "customerName": "Arun Kumar",
      "totalAmount": 120.00,
      "status": "SHIPPED",
      "items": [ ... ]
    }
  ]
}
```

**Error Responses:**
- `404` — Customer not found

---

### 6. Get My Orders (Agent Only)
**GET** `/api/orders/my`

Retrieves orders belonging to the currently authenticated agent (resolved from the JWT token).

**Access:** AGENT only

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Orders retrieved successfully",
  "data": [
    { ... },
    { ... }
  ]
}
```

**Error Responses:**
- `404` — No agent profile found for the current user

---

### 7. Get All Orders (Admin Only - Paginated)
**GET** `/api/orders`

Retrieves all orders with pagination, sorting, and filtering support.

**Access:** ADMIN only

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | Integer | `0` | Zero-based page index |
| `size` | Integer | `20` | Page size (max 100) |
| `sort` | String | `createdAt,desc` | Format: `field,direction` (e.g., `createdAt,desc`, `totalAmount,asc`) |
| `status` | OrderStatus | — | Filter by order status (PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED, RETURNED, FAILED) |

```bash
# Get page 0 with 20 items, newest first
curl "http://localhost:8085/api/orders?page=0&size=20&sort=createdAt,desc"

# Filter by status
curl "http://localhost:8085/api/orders?status=PENDING&size=10"
```

**Response (Paginated):**
```json
{
  "success": true,
  "message": "Orders retrieved successfully",
  "data": {
    "content": [
      {
        "id": "fbd1b509-09a4-4848-92aa-8ad00360b9f3",
        "orderNumber": "TM-D-20260614-001",
        "agentCode": null,
        "agentName": "Direct Order",
        "customerName": "Arun Kumar",
        "totalAmount": 120.00,
        "status": "PENDING",
        "orderSource": "DIRECT",
        "createdAt": "2026-06-14T22:23:36.573066"
      }
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 245,
    "totalPages": 13,
    "first": true,
    "last": false
  }
}
```

**Response Fields (PageResponse):**
| Field | Type | Description |
|-------|------|-------------|
| `content` | Array | Array of order objects |
| `pageNumber` | Integer | Current page (zero-based) |
| `pageSize` | Integer | Items per page |
| `totalElements` | Long | Total matching records |
| `totalPages` | Integer | Total pages available |
| `first` | Boolean | Whether this is the first page |
| `last` | Boolean | Whether this is the last page |

---

### 8. Update Order Status (Admin Only)
**PATCH** `/api/orders/{id}/status?status={status}`

Updates the order status up to `SHIPPED`. Once shipped, all further status transitions (DELIVERED, RETURNED, etc.) are handled through the delivery system.

**Access:** ADMIN only

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| id | UUID | Order ID |

**Query Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| status | OrderStatus | New status value |

**Allowed target statuses:**
| Status | Notes |
|--------|-------|
| `CONFIRMED` | Confirm order |
| `PROCESSING` | Start processing |
| `SHIPPED` | Mark shipped — auto-initializes `deliveryStatus = PENDING` |

**Blocked target statuses (use delivery/cancel endpoints instead):**
| Status | Alternative endpoint |
|--------|---------------------|
| `DELIVERED` | Use `PATCH /api/deliveries/{id}/status` with `DELIVERED` |
| `RETURNED` | Use delivery system |
| `CANCELLED` | Use `PATCH /api/orders/{id}/cancel` |

**Blocked current states (terminal):**
| State | Reason |
|-------|--------|
| `DELIVERED` | Terminal — cannot be changed |
| `CANCELLED` | Terminal — cannot be changed |
| `RETURNED` | Terminal — cannot be changed |

**Delivery initialization:**
| If status is set to | Delivery effect |
|--------------------|----------------|
| `SHIPPED` | Auto-initializes `deliveryStatus = PENDING` (if not already set) |

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Order status updated successfully",
  "data": {
    "id": "fbd1b509-09a4-4848-92aa-8ad00360b9f3",
    "orderNumber": "TM-D-20260614-001",
    "status": "CONFIRMED",
    ...
  }
}
```

---

### 9. Record Payment (Admin & Agent)
**POST** `/api/orders/{id}/payment`

Records a payment against an existing order. Supports full and partial payments. Cannot be used on already fully paid orders.

**Access:** ADMIN, AGENT

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| id | UUID | Order ID |

**Request Body:**
```json
{
  "amount": 2000.00,
  "paymentMethod": "CASH"
}
```

**Validation:**
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| amount | BigDecimal | Yes | Positive amount to record |
| paymentMethod | String | Yes | `CASH`, `COD`, `BANK_TRANSFER`, `CREDIT`, or `CARD_PAYMENT` |

**Behavior:**
- If `paidAmount + amount >= totalAmount` → order becomes `PAID`
- If `paidAmount + amount < totalAmount` → order becomes `PARTIALLY_PAID`
- If the payment method is `CREDIT`, the customer's `outstandingBalance` is updated

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Payment recorded successfully",
  "data": {
    "id": "fbd1b509-09a4-4848-92aa-8ad00360b9f3",
    "paymentStatus": "PAID",
    "paidAmount": 2000.00,
    "outstandingBalance": 0.00,
    "paymentMethod": "CASH",
    "paidAt": "2026-06-14T22:30:00.000000"
  }
}
```

**Error Responses:**
- 400: Order is already fully paid
- 404: Order not found

---

### 10. Cancel Order (Admin Only)
**PATCH** `/api/orders/{id}/cancel`

Cancels an order. Only orders with status `PENDING`, `CONFIRMED`, or `PROCESSING` can be cancelled.

**Access:** ADMIN only

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| id | UUID | Order ID |

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Order cancelled successfully",
  "data": {
    "id": "58b58ec9-849e-4e12-9700-d93c32d418f3",
    "orderNumber": "TM-D-20260614-002",
    "status": "CANCELLED",
    ...
  }
}
```

**Error Responses:**
- 400: Cannot cancel a shipped/delivered/returned order
- 400: Order is already cancelled

---

## Data Models

### OrderRequest
```json
{
  "agentCode": "String (optional; for admin only — set to create order for specific agent; omit for direct order)",
  "customerPhoneNumber": "String (required, e.g. 0111111111 — must match existing customer)",
  "paymentMethod": "PaymentMethod (required — CASH, COD, BANK_TRANSFER, CREDIT, CARD_PAYMENT)",
  "notes": "String (optional)",
  "paidAmount": "BigDecimal (optional — for CREDIT partial payment at creation; otherwise auto-determined by paymentMethod)",
  "items": [
    {
      "productItemCode": "String (required, e.g. PA-001)",
      "quantity": "Integer (required, positive)",
      "discountType": "String (optional; PERCENTAGE or FIXED — admin-only per-order discount)",
      "discountValue": "BigDecimal (optional; required if discountType is set; capped by product's maxDiscount)"
    }
  ]
}
```

### RecordPaymentRequest
```json
{
  "amount": "BigDecimal (required, positive)",
  "paymentMethod": "PaymentMethod (required — CASH, COD, BANK_TRANSFER, CREDIT, CARD_PAYMENT)"
}
```

### OrderResponse
```json
{
  "id": "UUID",
  "orderNumber": "String (e.g., TM-D-20260614-001)",
  "agentId": "UUID or null",
  "agentCode": "String or null",
  "agentName": "String or 'Direct Order'",
  "customerId": "UUID or null",
  "customerName": "String or null",
  "customerPhone": "String or null",
  "totalAmount": "BigDecimal",
  "status": "OrderStatus (PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED, RETURNED)",
  "paymentMethod": "PaymentMethod or null (CASH, COD, BANK_TRANSFER, CREDIT, CARD_PAYMENT)",
  "paymentStatus": "PaymentStatus (UNPAID, PARTIALLY_PAID, PAID, REFUNDED)",
  "paidAmount": "BigDecimal",
  "paidAt": "LocalDateTime or null",
  "outstandingBalance": "BigDecimal",
  "notes": "String",
  "createdById": "UUID",
  "createdByName": "String",
  "createdAt": "LocalDateTime",
  "updatedAt": "LocalDateTime",
  "district": "String or null (derived from customer's address district)",
  "deliveryStatus": "DeliveryStatus or null (PENDING, ASSIGNED, IN_TRANSIT, DELIVERED, FAILED)",
  "deliveryAssignedToId": "UUID or null",
  "deliveryAssignedToName": "String or null",
  "deliveryScheduledDate": "LocalDate or null",
  "deliveryNotes": "String or null",
  "orderSource": "AGENT or DIRECT",
  "items": [
    {
      "id": "UUID",
      "productId": "UUID",
      "productName": "String",
      "productItemCode": "String",
      "quantity": "Integer",
      "unitPrice": "BigDecimal",
      "originalUnitPrice": "BigDecimal",
      "subtotal": "BigDecimal",
      "discountType": "String or null",
      "discountValue": "BigDecimal or null",
      "costPrice": "BigDecimal or null"
    }
  ]
}
```

**New field — `costPrice`:** Each `OrderItem` now includes a `costPrice` field capturing the **weighted average cost (COGS)** at the time of sale. This is calculated as `inventory.averageCost × quantity` and is set when the order is created. Use this to compute per-order profit: `subtotal - costPrice = gross profit`.

### ApiResponse (Standard Response Wrapper)
```json
{
  "success": "Boolean",
  "message": "String",
  "data": "Object or null"
}
```

---

## Example: Full Order Flow

### Step 1: Login as Admin
```bash
curl -X POST http://localhost:8085/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@ebee.com","password":"admin123"}'
```

**Response:**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGci...",
    "userId": "...",
    "role": "ADMIN",
    ...
  }
}
```

### Step 2: Admin Creates Order for Agent
```bash
curl -X POST http://localhost:8085/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -d '{
    "agentCode": "AG-001",
    "customerPhoneNumber": "0111111111",
    "notes": "Customer requested express delivery",
    "items": [
      {
        "productItemCode": "PA-001",
        "quantity": 3
      }
    ]
  }'
```

**Response:**
```json
{
  "success": true,
  "message": "Order created successfully",
  "data": {
    "id": "...",
    "orderNumber": "TM-A-20260614-001",
    "agentCode": "TEST-002",
    "orderSource": "AGENT",
    ...
  }
}
```

### Step 3: Admin Gets All Orders
```bash
curl http://localhost:8085/api/orders \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

### Step 4: Update Order Status
```bash
curl -X PATCH "http://localhost:8085/api/orders/<ORDER_ID>/status?status=CONFIRMED" \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

### Step 5: Ship Order (auto-initiates delivery)
```bash
curl -X PATCH "http://localhost:8085/api/orders/<ORDER_ID>/status?status=SHIPPED" \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

### Agent Creating Their Own Order

1. Agent logs in:
```bash
curl -X POST http://localhost:8085/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"agent@ebee.com","password":"password123"}'
```

2. Agent creates order (agentCode is **ignored**; resolved from JWT automatically):
```bash
curl -X POST http://localhost:8085/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <AGENT_TOKEN>" \
  -d '{
    "customerPhoneNumber": "0111111111",
    "notes": "Agent placing order for customer",
    "items": [
      {
        "productItemCode": "PA-001",
        "quantity": 1
      }
    ]
  }'
```

**Response:**
```json
{
  "success": true,
  "message": "Order created successfully",
  "data": {
    "orderNumber": "TM-A-20260614-002",
    "agentCode": "AG即止AG-001",
    "orderSource": "AGENT",
    ...
  }
}
```

3. Agent views their own orders:
```bash
curl http://localhost:8085/api/orders/my \
  -H "Authorization: Bearer <AGENT_TOKEN>"
```

---

## Frontend Integration Guide

### Order Creation Flow

```
| Admin Dashboard  |
        |
    | Select Customer |
        |
    | Select Products |
        |
| Select Agent (optional) |
| - null  -> Direct Order |
| - code  -> Agent Order  |
        |
    |  POST /orders   |
        |
| Response:              |
| - orderSource: DIRECT  |
|   or AGENT             |
| - agentName: "Direct   |
|   Order" or Name       |
```

### Key Fields for Frontend Display

| Field | When to Show | Notes |
|-------|-------------|-------|
| `orderNumber` | Always | Unique identifier for the order |
| `customerName` | Always | Customer's name |
| `customerPhone` | Always | Customer's phone number |
| `totalAmount` | Always | Order total |
| `status` | Always | Current order status (PENDING, CONFIRMED, etc.) |
| `orderSource` | Always | Shows "DIRECT" or "AGENT" |
| `agentName` | When orderSource == "AGENT" | Shows agent name; "Direct Order" for direct |
| `agentCode` | When orderSource == "AGENT" | Agent code |
| `district` | Always (if available) | Delivery district |
| `createdAt` | Always | Order creation timestamp |
| `items` | Order detail view | List of ordered products |

### Order Status Flow

```
PENDING -> CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED
    ^                                          |
    |                                          |
    +-----------------------------------------> CANCELLED
    |                                          |
    +-----------------------------------------> RETURNED
```

**Terminal statuses (cannot be changed):**
- `DELIVERED`
- `CANCELLED`
- `RETURNED`

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
| 400 | Bad Request - Invalid input / Agent not active / Product not active / Cannot cancel shipped order |
| 401 | Unauthorized - Invalid/missing token |
| 403 | Forbidden - Insufficient permissions |
| 404 | Not Found - Agent/Customer/Product/Order not found |
| 500 | Internal Server Error |

---

## Notes

1. **Stock Management:** Creating an order automatically reduces product inventory
2. **Order Number Format:**
   - Direct orders: `{DISTRICT}-D-{DATE}-{SEQUENCE}` (e.g., `TM-D-20260614-001`)
   - Agent orders: `{DISTRICT}-A-{DATE}-{SEQUENCE}` (e.g., `TM-A-20260614-001`)
3. **Agent Validation:** Only active agents can have orders created for them
4. **Product Validation:** Only active products can be ordered
5. **Authorization:** Agents can only see orders they created, admins see all
6. **Auto-Commission:** If the order has an agent, a commission is auto-created in `CALCULATED` status using the best matching commission config or agent's default rate. Direct orders (`orderSource: DIRECT`) do not generate commissions. The commission is auto-approved when the order/delivery reaches `DELIVERED`.
7. **Customer Identification:** Provide `customerPhoneNumber` to link the order to an existing customer; fails with 404 if the phone is not found
8. **Cancel Rollback:** Cancelling an order restores stock AND rolls back the associated commission (reverts agent totals if the commission was paid)
9. **District Derivation:** The `district` field in the response is derived from the customer's address district and is read-only
10. **OrderSource:** Each order has an `orderSource` field — `AGENT` (created with an agentCode) or `DIRECT` (admin order without an agent). This is set at creation time and cannot be changed.
