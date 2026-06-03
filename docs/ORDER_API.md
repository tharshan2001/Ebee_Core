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

**Request Body:**
```json
{
  "agentCode": "AG-001",
  "customerPhoneNumber": "+94771234567",
  "notes": "Optional order notes",
  "items": [
    {
      "productItemCode": "PA-001",
      "quantity": 2
    }
  ]
}
```

**Validation:**
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| agentCode | String | Yes (for non-admin) | Must match an active agent's code |
| customerPhoneNumber | String | Yes | Must match an existing customer's phone number |
| notes | String | No | Max 1000 characters |
| items | Array | Yes | At least 1 item |
| items[].productItemCode | String | Yes | Must match an active product's item code |
| items[].quantity | Integer | Yes | Positive integer |

**Success Response (201 Created):**
```json
{
  "success": true,
  "message": "Order created successfully",
  "data": {
    "id": "380111f3-301b-4fda-8242-0f852f30acf6",
    "orderNumber": "ORD-20260508221802",
    "agentId": "478de22c-9519-4503-829e-56b0236b24ce",
    "agentCode": "NEW001",
    "agentName": "New Test",
    "customerId": "16cd9e90-e123-40e1-91a5-d2f00ed0add7",
    "customerName": "Arun Kumar",
    "customerPhone": "+94111111111",
    "totalAmount": 119.98,
    "status": "PENDING",
    "notes": "Test order from admin",
    "createdById": "5b718629-785e-4fad-a475-01e7c5957670",
    "createdByName": "Admin User",
    "createdAt": "2026-05-08T22:18:03.000209",
    "updatedAt": "2026-05-08T22:18:03.000217",
    "items": [
      {
        "id": "d09a3e09-486a-46ad-bcec-e03be4468600",
        "productId": "171ab477-cba6-4589-934d-01906d480852",
        "productName": "Updated Product A",
        "productItemCode": "PROD-A",
        "quantity": 2,
        "unitPrice": 59.99,
        "subtotal": 119.98
      }
    ]
  }
}
```

**Error Responses:**
- 400: Agent not active / Product not active / Insufficient stock
- 401: Unauthorized (invalid/missing token)
- 403: Forbidden (role not authorized)

---

### 2. Get Order by ID
**GET** `/api/orders/{id}`

Retrieves a specific order by its UUID.

**Access:** ADMIN, AGENT

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| id | UUID | Order ID |

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
| orderNumber | String | Order number (e.g., ORD-20260508221802) |

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
| agentId | UUID | Agent ID |

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

Retrieves all orders for a specific customer.

**Access:** ADMIN, AGENT

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| customerId | UUID | Customer ID |

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Orders retrieved successfully",
  "data": [
    {
      "id": "380111f3-301b-4fda-8242-0f852f30acf6",
      "orderNumber": "ORD-20260508221802",
      "agentCode": "NEW001",
      "customerName": "Arun Kumar",
      "totalAmount": 119.98,
      "status": "PENDING",
      "items": [ ... ]
    }
  ]
}
```

**Error Responses:**
- `404` — Customer not found

---

### 6. Get All Orders (Admin Only)
**GET** `/api/orders`

Retrieves all orders in the system.

**Access:** ADMIN only

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

### 7. Update Order Status (Admin Only)
**PATCH** `/api/orders/{id}/status?status={status}`

Updates the status of an order.

**Access:** ADMIN only

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| id | UUID | Order ID |

**Query Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| status | OrderStatus | New status value |

**Order Status Values:**
```
PENDING       - Order created, awaiting confirmation
CONFIRMED     - Order confirmed by admin
PROCESSING    - Order is being processed
SHIPPED       - Order has been shipped
DELIVERED     - Order delivered to customer
CANCELLED     - Order cancelled
RETURNED      - Order returned
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Order status updated successfully",
  "data": {
    "id": "380111f3-301b-4fda-8242-0f852f30acf6",
    "orderNumber": "ORD-20260508221802",
    "status": "CONFIRMED",
    ...
  }
}
```

---

### 8. Cancel Order (Admin Only)
**PATCH** `/api/orders/{id}/cancel`

Cancels an order.

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
  "data": { ... }
}
```

---

## Data Models

### OrderRequest
```json
{
  "agentCode": "String (required for non-admin, e.g. AG-001)",
  "customerPhoneNumber": "String (optional, must match existing customer)",
  "notes": "String (optional)",
  "items": [
    {
      "productItemCode": "String (required, e.g. PA-001)",
      "quantity": "Integer (required, positive)"
    }
  ]
}
```

### OrderResponse
```json
{
  "id": "UUID",
  "orderNumber": "String",
  "agentId": "UUID",
  "agentCode": "String",
  "agentName": "String",
  "customerId": "UUID or null",
  "customerName": "String or null",
  "customerPhone": "String or null",
  "totalAmount": "BigDecimal",
  "status": "OrderStatus",
  "notes": "String",
  "createdById": "UUID",
  "createdByName": "String",
  "createdAt": "LocalDateTime",
  "updatedAt": "LocalDateTime",
  "items": [
    {
      "id": "UUID",
      "productId": "UUID",
      "productName": "String",
      "productItemCode": "String",
      "quantity": "Integer",
      "unitPrice": "BigDecimal",
      "subtotal": "BigDecimal"
    }
  ]
}
```

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

### Step 2: Get Active Products
```bash
curl http://localhost:8085/api/products/active \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

### Step 3: Get All Agents
```bash
curl http://localhost:8085/api/agents \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

### Step 4: Create Order
```bash
curl -X POST http://localhost:8085/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -d '{
    "agentCode": "AG-001",
    "customerPhoneNumber": "+94771234567",
    "notes": "Customer requested express delivery",
    "items": [
      {
        "productItemCode": "PA-001",
        "quantity": 3
      }
    ]
  }'
```

### Step 5: Update Order Status
```bash
curl -X PATCH "http://localhost:8085/api/orders/380111f3-301b-4fda-8242-0f852f30acf6/status?status=CONFIRMED" \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

### Agent Creating Order

1. Agent logs in:
```bash
curl -X POST http://localhost:8085/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"agent@ebee.com","password":"password123"}'
```

2. Agent creates order (using their own agentCode):
```bash
curl -X POST http://localhost:8085/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <AGENT_TOKEN>" \
  -d '{
    "agentCode": "AG-001",
    "customerPhoneNumber": "+94771234567",
    "notes": "Customer order",
    "items": [
      {
        "productItemCode": "PA-001",
        "quantity": 1
      }
    ]
  }'
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
| 400 | Bad Request - Invalid input |
| 401 | Unauthorized - Invalid/missing token |
| 403 | Forbidden - Insufficient permissions |
| 404 | Not Found - Resource not found |
| 500 | Internal Server Error |

---

## Notes

1. **Stock Management:** Creating an order automatically reduces product inventory
2. **Order Number:** System generates unique order numbers (e.g., ORD-20260508221802)
3. **Agent Validation:** Only active agents can have orders created for them
4. **Product Validation:** Only active products can be ordered
5. **Authorization:** Agents can only see orders they created, admins see all
6. **Auto-Commission:** If the order has an agent, a commission is auto-created in `CALCULATED` status using the best matching commission config or agent's default rate
7. **Customer Identification:** Provide `customerPhoneNumber` to link the order to an existing customer; fails with 404 if the phone is not found
8. **Cancel Rollback:** Cancelling an order restores stock AND rolls back the associated commission (reverts agent totals if the commission was paid)