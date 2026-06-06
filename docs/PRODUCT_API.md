````# Product API Reference

## Base URL

```
http://localhost:8085/api/products
```

## Authentication

All product endpoints require ADMIN role:
```
Authorization: Bearer <JWT_TOKEN>
```

## Category Validation

The `category` field must match an **active** category name managed via the [Categories API](CATEGORY_API.md). If the category is missing, misspelled, or has been soft-deleted, the API returns `400 Category not found`.

You can list all valid categories for the dropdown:

```bash
curl http://localhost:8085/api/categories -H "Authorization: Bearer <TOKEN>"
```

---

## Endpoints

### Get Active Products

**GET** `/api/products/active`

**Response:**
```json
{
  "success": true,
  "message": "Active products retrieved successfully",
  "data": [
    {
      "id": "171ab477-cba6-4589-934d-01906d480852",
      "name": "Updated Product A",
      "itemCode": "PA-001",
      "description": "Updated description",
      "category": "Electronics",
      "price": 59.99,
      "costPrice": 30.0,
      "isActive": true,
      "inventory": {
        "availableQuantity": 141,
        "isLowStock": false
      }
    }
  ]
}
```

---

### Delete Product (Soft-Delete)

**DELETE** `/api/products/{id}`

Deactivates a product by setting `isActive = false`. This is a soft-delete — the product record remains in the database but is excluded from `GET /active` results and cannot be used in new orders.

**Auth:** ADMIN

```bash
curl -X DELETE http://localhost:8085/api/products/<UUID> \
  -H "Authorization: Bearer <TOKEN>"
```

**Response:**
```json
{
  "success": true,
  "message": "Product deleted successfully",
  "data": null
}
```

---

### Activate Product (Re-activation)

**PATCH** `/api/products/{id}/activate`

Re-activates a previously deactivated (soft-deleted) product. Only works if the product is currently deactivated.

**Auth:** ADMIN

```bash
curl -X PATCH http://localhost:8085/api/products/<UUID>/activate \
  -H "Authorization: Bearer <TOKEN>"
```

**Success Response (200):**
```json
{
  "success": true,
  "message": "Product activated successfully",
  "data": {
    "id": "746f35da-dbee-4e2b-a7ab-23fcc4e79a13",
    "name": "Browser",
    "itemCode": "BRW001",
    "isActive": true,
    ...
  }
}
```

**Error Response (400) — already active:**
```json
{
  "success": false,
  "message": "Product is already active",
  "data": null
}
```

---

> Products are referenced by `itemCode` (e.g., `PA-001`) when creating orders, not by UUID.
````