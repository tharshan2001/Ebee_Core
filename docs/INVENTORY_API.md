# Inventory API

**Base URL:** `http://localhost:8085/api/inventory`

## Authentication

All endpoints require JWT:
```
Authorization: Bearer <JWT_TOKEN>
```

**Role:** Some read endpoints are accessible to ADMIN and AGENT roles. Others (`list all`, `low-stock`) require ADMIN. Mutation endpoints (`adjust`, `reserve`, `release`, `order-stock`) require `ADMIN`.

---

## Get Inventory by ID

**GET** `/api/inventory/{id}`

**Access:** ADMIN, AGENT

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Inventory record ID |

**Response:**
```json
{
  "success": true,
  "message": "Inventory retrieved successfully",
  "data": {
    "id": "f5b71829-785e-4adf-a475-01e7c5957670",
    "productId": "a1b71829-785e-4adf-a475-01e7c5957670",
    "productName": "iPhone 14",
    "productItemCode": "IP-001",
    "quantity": 125,
    "reservedQuantity": 5,
    "availableQuantity": 120,
    "reorderLevel": 10,
    "isLowStock": false,
    "createdAt": "2026-05-15T10:30:00",
    "updatedAt": "2026-06-09T14:00:00"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Inventory record ID |
| `productId` | `UUID` | Associated product ID |
| `productName` | `String` | Product name |
| `productItemCode` | `String` | Product item code |
| `quantity` | `Integer` | Total stock quantity |
| `reservedQuantity` | `Integer` | Quantity reserved for pending orders |
| `availableQuantity` | `Integer` | Quantity available for sale (quantity - reservedQuantity) |
| `reorderLevel` | `Integer` | Threshold below which stock is considered low |
| `averageCost` | `BigDecimal` | Weighted average cost per unit (updated on PO receipt via WAC formula) |
| `isLowStock` | `Boolean` | Whether current stock is below reorder level |
| `createdAt` | `LocalDateTime` | Record creation timestamp |
| `updatedAt` | `LocalDateTime` | Last update timestamp |

---

## Get Inventory by Product ID

**GET** `/api/inventory/product/{productId}`

**Access:** ADMIN, AGENT

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `productId` | `UUID` | Product ID |

**Response:** Same shape as `GET /api/inventory/{id}`.

---

## List All Inventories

**GET** `/api/inventory`

**Access:** ADMIN only

Returns inventory records for all products.

**Response:**
```json
{
  "success": true,
  "message": "Inventories retrieved successfully",
  "data": [ { "...same shape as get by ID..." } ]
}
```

---

## Low Stock Inventories

**GET** `/api/inventory/low-stock`

**Access:** ADMIN only

Returns all inventory records where `currentStock < reorderLevel`.

**Response:**
```json
{
  "success": true,
  "message": "Low stock inventories retrieved successfully",
  "data": [ { "...same shape as get by ID..." } ]
}
```

---

## Adjust Stock

**POST** `/api/inventory/adjust`

**Access:** ADMIN

Manually adjusts stock levels. Creates a stock movement record with the specified movement type.

**Request Body:**
```json
{
  "productId": "a1b71829-785e-4adf-a475-01e7c5957670",
  "quantityChange": -10,
  "movementType": "MANUAL_ADJUSTMENT",
  "notes": "Damaged items removed"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `productId` | `UUID` | Yes | Product ID |
| `quantityChange` | `Integer` | Yes | Positive for addition, negative for reduction |
| `movementType` | `StockMovementType` | Yes | Type of movement |
| `notes` | `String` | No | Optional notes |

**Response (201):**
```json
{
  "success": true,
  "message": "Stock adjusted successfully",
  "data": { "...same shape as get by ID..." }
}
```

---

## Reserve Stock

**POST** `/api/inventory/reserve`

**Access:** ADMIN

Reserves a quantity of stock for an order. Deducts from `availableQuantity` and adds to `reservedQuantity`.

**Query Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `productId` | `UUID` | Yes | Product ID |
| `quantity` | `Integer` | Yes | Quantity to reserve |

**Response (201):** Updated inventory record.

---

## Release Reserved Stock

**POST** `/api/inventory/release`

**Access:** ADMIN

Releases previously reserved stock back to available inventory.

**Query Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `productId` | `UUID` | Yes | Product ID |
| `quantity` | `Integer` | Yes | Quantity to release |

**Response (200):** Updated inventory record.

---

## Get Stock History

**GET** `/api/inventory/product/{productId}/history`

**Access:** ADMIN, AGENT

Returns stock movement history for a product.

**Response:**
```json
{
  "success": true,
  "message": "Stock history retrieved successfully",
  "data": [
    {
      "id": "c3b71829-785e-4adf-a475-01e7c5957670",
      "productId": "a1b71829-785e-4adf-a475-01e7c5957670",
      "productName": "iPhone 14",
      "productItemCode": "IP-001",
      "quantityChange": -1,
      "quantityBefore": 126,
      "quantityAfter": 125,
      "movementType": "ORDER_DISPATCHED",
      "notes": "Order #1234 dispatched",
      "createdById": "7a5a36c9-8423-464c-8697-9b30f0025ce0",
      "createdByName": "Admin User",
      "createdByEmail": "admin@ebee.com",
      "createdAt": "2026-06-09T14:00:00"
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Stock movement record ID |
| `productId` | `UUID` | Associated product ID |
| `productName` | `String` | Product name |
| `productItemCode` | `String` | Product item code |
| `quantityChange` | `Integer` | Change amount (negative for reduction) |
| `quantityBefore` | `Integer` | Stock level before change |
| `quantityAfter` | `Integer` | Stock level after change |
| `movementType` | `StockMovementType` | Type of movement |
| `notes` | `String` | Optional notes |
| `createdById` | `UUID` | User who performed the change |
| `createdByName` | `String` | Name of the user |
| `createdByEmail` | `String` | Email of the user |
| `createdAt` | `LocalDateTime` | When the movement occurred |

---

## Record Order Stock

**POST** `/api/inventory/order-stock`

**Access:** ADMIN

Records a stock movement specifically for order-related stock changes.

**Query Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `productId` | `UUID` | Yes | Product ID |
| `quantity` | `Integer` | Yes | Quantity change |
| `movementType` | `StockMovementType` | Yes | Type of movement |
| `notes` | `String` | No | Optional notes |

**Response (201):** Stock movement record.

---

## Error Responses

| HTTP Status | Scenario |
|-------------|----------|
| 401 / 403 | Missing or invalid JWT, or non-ADMIN role for mutation endpoints |
| 404 | Inventory or product not found |
| 400 | Insufficient stock for reserve/release, or validation failure |
| 500 | Server error |
