# Product API Reference

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
      "sku": "PA-001",
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

> Products are referenced by `sku` (e.g., `PA-001`) when creating orders, not by UUID.
