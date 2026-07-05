# Purchase Order API Reference

## Base URL

```
http://localhost:8085/api/purchase-orders
```

## Authentication

All endpoints require ADMIN role:

```
Authorization: Bearer <JWT_TOKEN>
```

---

## Status Lifecycle

```
DRAFT ──► PENDING ──► APPROVED ──► RECEIVED
  │          │            │
  └──────────┴────────────┴──► CANCELLED (any state)
```

- **DRAFT** — initial state after creation
- **PENDING** — ready for review
- **APPROVED** — approved by admin
- **RECEIVED** — stock received; triggers inventory update via `StockMovementType.PURCHASE`
- **CANCELLED** — can be set from any state; no inventory changes

---

## Endpoints

### Create Purchase Order

**POST** `/api/purchase-orders`

**Request body:**
```json
{
  "supplierId": "d1c2e3f4-5678-90ab-cdef-1234567890ab",
  "notes": "Monthly restock order",
  "items": [
    {
      "productId": "a1b2c3d4-5678-90ab-cdef-1234567890ab",
      "quantity": 50,
      "unitPrice": 2500.00
    },
    {
      "productId": "e5f6g7h8-5678-90ab-cdef-1234567890ab",
      "quantity": 100,
      "unitPrice": 150.00
    }
  ]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `supplierId` | `UUID` | Yes | Supplier ID |
| `notes` | `String` | No | Internal notes |
| `items[].productId` | `UUID` | Yes | Product ID |
| `items[].quantity` | `Integer` | Yes | Quantity (must be positive) |
| `items[].unitPrice` | `BigDecimal` | Yes | Unit cost price (must be positive) |

**Response (201):**
```json
{
  "success": true,
  "message": "Purchase order created successfully",
  "data": {
    "id": "f1e2d3c4-b5a6-7890-abcd-ef1234567890",
    "poNumber": "PO-20260705-001",
    "supplierId": "d1c2e3f4-5678-90ab-cdef-1234567890ab",
    "supplierName": "Tech Distributors (Pvt) Ltd",
    "status": "DRAFT",
    "totalAmount": 140000.00,
    "notes": "Monthly restock order",
    "createdById": "a1a2a3a4-b5b6-7890-abcd-ef1234567890",
    "createdByName": "Admin User",
    "items": [
      {
        "id": "x1x2x3x4-b5b6-7890-abcd-ef1234567890",
        "productId": "a1b2c3d4-5678-90ab-cdef-1234567890ab",
        "productName": "Product A",
        "productItemCode": "PA-001",
        "quantity": 50,
        "unitPrice": 2500.00,
        "subtotal": 125000.00
      },
      {
        "id": "y1y2y3y4-b5b6-7890-abcd-ef1234567890",
        "productId": "e5f6g7h8-5678-90ab-cdef-1234567890ab",
        "productName": "Product B",
        "productItemCode": "PB-001",
        "quantity": 100,
        "unitPrice": 150.00,
        "subtotal": 15000.00
      }
    ],
    "attachments": [],
    "createdAt": "2026-07-05T16:00:00",
    "updatedAt": "2026-07-05T16:00:00"
  }
}
```

---

### Get Purchase Order by ID

**GET** `/api/purchase-orders/{id}`

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Purchase order ID |

**Response (200):** Same shape as create response (includes items and attachments).

---

### List Purchase Orders (Paginated)

**GET** `/api/purchase-orders?search=PO-20260705&page=0&size=20&sortBy=createdAt&sortDir=desc`

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `search` | `String` | — | Optional filter by PO number prefix |
| `page` | `int` | `0` | Page number (0-based) |
| `size` | `int` | `20` | Page size |
| `sortBy` | `String` | `createdAt` | Sort field |
| `sortDir` | `String` | `desc` | Sort direction (`asc` or `desc`) |

**Response (200):**
```json
{
  "success": true,
  "message": "Purchase orders retrieved successfully",
  "data": {
    "content": [ { ... PO objects ... } ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 5,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

---

### List by Supplier

**GET** `/api/purchase-orders/by-supplier/{supplierId}`

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `supplierId` | `UUID` | Supplier ID |

Returns all purchase orders for a supplier (unpaginated).

---

### Update Status

**PATCH** `/api/purchase-orders/{id}/status`

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Purchase order ID |

**Request body:**
```json
{
  "status": "PENDING"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `status` | `PurchaseOrderStatus` | Yes | New status: `DRAFT`, `PENDING`, `APPROVED`, `RECEIVED`, `CANCELLED` |

**Status transition rules:**

| Current | → Allowed |
|---------|-----------|
| `DRAFT` | `PENDING`, `CANCELLED` |
| `PENDING` | `APPROVED`, `CANCELLED` |
| `APPROVED` | `RECEIVED`, `CANCELLED` |
| `RECEIVED` | (none) |
| `CANCELLED` | (none) |

When transitioning to **RECEIVED**, the system:
1. Records stock movement for each item (increases inventory quantities)
2. Recalculates the **weighted average cost (WAC)** for each product:
   ```
   newAvgCost = (currentQty × currentAvgCost + receivedQty × unitPrice) / (currentQty + receivedQty)
   ```
   The updated average is stored on the inventory record and used for COGS calculation on future sales.

**Response (200):** Returns updated PO with new status.

**Error (400):**
```json
{
  "success": false,
  "message": "Cannot transition from DRAFT to RECEIVED",
  "data": null
}
```

---

### Add Attachment

**POST** `/api/purchase-orders/{orderId}/attachments`

Adds a file attachment (invoice, delivery note, etc.) to a purchase order. Files are uploaded to S3 under `purchases/{orderId}/`.

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `orderId` | `UUID` | Purchase order ID |

**Request:** `multipart/form-data`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `file` | `File` | Yes | File to upload (PDF, JPEG, PNG, DOCX, XLSX) — max 10MB |

**Response (201):** Returns the PO with the new attachment included in the `attachments` array.

---

### Delete Attachment

**DELETE** `/api/purchase-orders/{orderId}/attachments/{attachmentId}`

Removes an attachment from S3 and the database.

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `orderId` | `UUID` | Purchase order ID |
| `attachmentId` | `UUID` | Attachment ID |

**Response (200):**
```json
{
  "success": true,
  "message": "Attachment deleted successfully",
  "data": null
}
```

---

## PO Number Format

Purchase order numbers follow the pattern:

```
PO-YYYYMMDD-NNN
```

- `PO` — static prefix
- `YYYYMMDD` — current date
- `NNN` — auto-incrementing sequence (001, 002, …), reset daily

Example: `PO-20260705-001`

If a duplicate number collision occurs (very rare under concurrent creation), the system retries automatically up to 5 times.

---

## Error Responses

| Code | Scenario |
|------|----------|
| 400 | Validation failed / Invalid status transition / Invalid file type |
| 404 | Purchase order / Supplier / Product / Attachment not found |
| 409 | Duplicate PO number (should self-resolve via retry) |
