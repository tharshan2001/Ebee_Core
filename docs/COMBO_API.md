# Combo API Reference

**Base URL:** `http://localhost:8085/api`

## Authentication

```
Authorization: Bearer <JWT_TOKEN>
```

| Endpoint | Role |
|----------|------|
| `GET /api/combos/**` | Public |
| `POST /api/admin/combos` | ADMIN |
| `PUT /api/admin/combos/{id}` | ADMIN |
| `DELETE /api/admin/combos/{id}` | ADMIN |
| `GET /api/admin/combos` | ADMIN |

---

## Overview

A **combo** (product bundle) groups multiple products into a single sellable unit with an optional pricing rule. Combos appear as a single line item in orders.

### Rule Types

| Type | Behavior |
|------|----------|
| *(none)* | Plain bundle. Customer pays `sum(product.price * quantity)`. |
| `FREE_PRODUCT` | Admin picks an exact product to include free. Customer pays the combo price; free item appears as a separate order line at price 0. |
| `DISCOUNT` | Applies a `FIXED` or `PERCENTAGE` discount off the combo price. |

### Immutability

Once a combo has been used in an order, it **cannot be edited or deleted**:
- **Update** clones a new combo with a fresh UUID and deactivates the old one.
- **Delete** throws `ComboImmutabilityException` (HTTP 400).

---

## Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Combo ID |
| `name` | `String` | Combo name |
| `description` | `String` | Combo description |
| `comboPrice` | `BigDecimal` | Auto-calculated: `sum(product.price * quantity)` |
| `isActive` | `Boolean` | Whether the combo is active and orderable |
| `items` | `ComboItemResponse[]` | Products in the combo |
| `rule` | `ComboRuleResponse` | Pricing rule (nullable) |
| `createdAt` | `LocalDateTime` | Creation timestamp |
| `updatedAt` | `LocalDateTime` | Last update timestamp |

### ComboItemResponse

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Item ID |
| `productId` | `UUID` | Product ID |
| `productName` | `String` | Product name |
| `productItemCode` | `String` | Product item code |
| `quantity` | `Integer` | Quantity of this product in the combo |

### ComboRuleResponse

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Rule ID |
| `ruleType` | `String` | `FREE_PRODUCT` or `DISCOUNT` |
| `freeProductId` | `UUID` | Product ID of the free item (FREE_PRODUCT only) |
| `freeProductName` | `String` | Name of the free item (FREE_PRODUCT only) |
| `discountType` | `String` | `FIXED` or `PERCENTAGE` (DISCOUNT only) |
| `discountValue` | `BigDecimal` | Discount amount or percentage (DISCOUNT only) |

---

## Endpoints

### List Active Combos (Public)

**GET** `/api/combos`

Returns all active combos. No authentication required.

**Query Parameters:**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `page` | `0` | Page number |
| `size` | `20` | Page size |
| `sortBy` | `createdAt` | Sort field |
| `sortDir` | `desc` | Sort direction |

```bash
curl "http://localhost:8085/api/combos?page=0&size=10"
```

**Response (200):**
```json
{
  "success": true,
  "message": "Combos retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Hair Care Bundle",
        "description": "Shampoo + Conditioner + Free Brush",
        "comboPrice": 1750.00,
        "isActive": true,
        "items": [
          {
            "id": "...",
            "productId": "...",
            "productName": "Shampoo",
            "productItemCode": "PROD-001",
            "quantity": 2
          },
          {
            "id": "...",
            "productId": "...",
            "productName": "Conditioner",
            "productItemCode": "PROD-002",
            "quantity": 1
          }
        ],
        "rule": {
          "id": "...",
          "ruleType": "FREE_PRODUCT",
          "freeProductId": "...",
          "freeProductName": "Free Brush",
          "discountType": null,
          "discountValue": null
        },
        "createdAt": "2026-07-15T10:00:00",
        "updatedAt": "2026-07-15T10:00:00"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 10,
    "number": 0
  }
}
```

---

### Get Combo by ID

**GET** `/api/combos/{id}`

Returns a single combo with full details. Public endpoint.

```bash
curl "http://localhost:8085/api/combos/550e8400-e29b-41d4-a716-446655440000"
```

**Response (200):**
```json
{
  "success": true,
  "message": "Combo retrieved successfully",
  "data": { ... }
}
```

**Error (404):**
```json
{
  "success": false,
  "message": "Combo not found with id: 550e8400-..."
}
```

---

### Create Combo (Admin)

**POST** `/api/admin/combos`

**Access:** ADMIN

```bash
curl -X POST "http://localhost:8085/api/admin/combos" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Hair Care Bundle",
    "description": "Shampoo + Conditioner + Free Brush",
    "items": [
      { "productId": "uuid-product-1", "quantity": 2 },
      { "productId": "uuid-product-2", "quantity": 1 }
    ],
    "rule": {
      "ruleType": "FREE_PRODUCT",
      "freeProductId": "uuid-free-product"
    }
  }'
```

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | `String` | yes | Combo name |
| `description` | `String` | no | Combo description |
| `items` | `ComboItemRequest[]` | yes | At least one product |
| `rule` | `ComboRuleRequest` | no | Pricing rule |

**ComboItemRequest:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `productId` | `UUID` | yes | Must be an active product |
| `quantity` | `Integer` | yes | Quantity of this product in the combo |

**ComboRuleRequest (FREE_PRODUCT):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `ruleType` | `String` | yes | `"FREE_PRODUCT"` |
| `freeProductId` | `UUID` | yes | Must be an active product |

**ComboRuleRequest (DISCOUNT):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `ruleType` | `String` | yes | `"DISCOUNT"` |
| `discountType` | `String` | yes | `"FIXED"` or `"PERCENTAGE"` |
| `discountValue` | `BigDecimal` | yes | Must be >= 0; percentage must be <= 100 |

**Response (201):**
```json
{
  "success": true,
  "message": "Combo created successfully",
  "data": { ... }
}
```

**Errors:**
- `400` — Inactive product in combo, missing rule fields, negative discount, percentage > 100
- `404` — Product not found

---

### Update Combo (Admin)

**PUT** `/api/admin/combos/{id}`

**Access:** ADMIN

If the combo has **not** been used in any order, it is updated in place. If it **has** been used, a new combo is cloned (new UUID) and the old one is deactivated.

```bash
curl -X PUT "http://localhost:8085/api/admin/combos/{id}" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Updated Bundle",
    "items": [
      { "productId": "uuid-product-1", "quantity": 3 }
    ]
  }'
```

**Response (200):**
```json
{
  "success": true,
  "message": "Combo updated successfully",
  "data": { ... }
}
```

---

### Delete Combo (Admin)

**DELETE** `/api/admin/combos/{id}`

**Access:** ADMIN

Soft-deletes (deactivates) the combo. Throws `400` if the combo has been used in orders.

```bash
curl -X DELETE "http://localhost:8085/api/admin/combos/{id}" \
  -H "Authorization: Bearer <TOKEN>"
```

**Response (200):**
```json
{
  "success": true,
  "message": "Combo deactivated successfully"
}
```

**Error (400):**
```json
{
  "success": false,
  "message": "Cannot delete combo 'Hair Care Bundle' — it has been used in orders. Deactivate it instead."
}
```

---

### List All Combos (Admin)

**GET** `/api/admin/combos`

**Access:** ADMIN

Returns all combos (active and inactive). Same query parameters as the public listing.

```bash
curl "http://localhost:8085/api/admin/combos?page=0&size=50" \
  -H "Authorization: Bearer <TOKEN>"
```

---

## Using Combos in Orders

To order a combo, include a combo item in the order request instead of (or alongside) regular product items.

### OrderItemRequest — Combo

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `comboId` | `UUID` | yes | ID of the active combo |
| `quantity` | `Integer` | yes | Number of combo bundles |
| `productItemCode` | `String` | — | Ignored when `comboId` is set |

**Example — Combo only:**
```json
{
  "customerPhoneNumber": "0771234567",
  "paymentMethod": "CASH",
  "items": [
    { "comboId": "combo-uuid", "quantity": 1 }
  ]
}
```

**Example — Combo + regular products:**
```json
{
  "customerPhoneNumber": "0771234567",
  "paymentMethod": "CASH",
  "items": [
    { "comboId": "combo-uuid", "quantity": 1 },
    { "productItemCode": "PROD-003", "quantity": 2 }
  ]
}
```

### Order Item Response — Combo Fields

| Field | Type | Description |
|-------|------|-------------|
| `comboId` | `UUID` | Combo ID (null for regular items) |
| `comboName` | `String` | Combo name (null for regular items) |
| `isFreeProduct` | `Boolean` | `true` for the free product line added by a FREE_PRODUCT rule |

### Pricing Rules at Order Time

| Rule | Customer Price | Stock Impact |
|------|---------------|--------------|
| *(none)* | `comboPrice` (sum of products) | All combo products deducted |
| `FREE_PRODUCT` | `comboPrice` (free item at 0) | All combo products + free product deducted |
| `DISCOUNT` | `comboPrice - discount` | All combo products deducted |

### Validation at Order Time

- All combo products must be active
- Free product (if rule is FREE_PRODUCT) must be active
- Sufficient stock for all combo products (and free product)
- Combo itself must be active
