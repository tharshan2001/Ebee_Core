# Product API Reference

**Base URL:** `http://localhost:8085/api/products`

## Authentication

```
Authorization: Bearer <JWT_TOKEN>
```

**Role:** Read endpoints (`GET`) are public. Mutation endpoints (`POST`, `PATCH`, `DELETE`) require `ADMIN`.

---

## Category Validation

The `category` field must match an **active** category name managed via the [Categories API](CATEGORY_API.md). Returns `400 Category not found` if missing, misspelled, or soft-deleted.

```bash
curl http://localhost:8085/api/categories -H "Authorization: Bearer <TOKEN>"
```

---

## Common Response Fields

Product responses include:

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Product ID |
| `name` | `String` | Product name |
| `itemCode` | `String` | Unique item code (used in orders) |
| `description` | `String` | Product description |
| `category` | `String` | Category name |
| `price` | `BigDecimal` | Selling price |
| `costPrice` | `BigDecimal` | Cost price |
| `isActive` | `Boolean` | Whether product is active/orderable |
| `imageUrls` | `String[]` | S3 URLs of product images |
| `inventory` | `Object` | Inventory snapshot (`availableQuantity`, `isLowStock`) |
| `createdAt` | `LocalDateTime` | Creation timestamp |
| `updatedAt` | `LocalDateTime` | Last update timestamp |

---

## Endpoints

### Get Active Products

**GET** `/api/products/active`

**Access:** Public

Returns products where `isActive = true`. These are the only products available for new orders.

```bash
curl http://localhost:8085/api/products/active
```

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
      "imageUrls": [
        "https://s3.amazonaws.com/.../image1.jpg"
      ],
      "inventory": {
        "availableQuantity": 141,
        "isLowStock": false
      },
      "createdAt": "2026-05-15T10:30:00",
      "updatedAt": "2026-06-09T14:00:00"
    }
  ]
}
```

---

### Get All Products

**GET** `/api/products`

**Access:** Public

Returns all products (active and inactive).

---

### Get Product by ID

**GET** `/api/products/{id}`

**Access:** Public

---

### Get Product by Item Code

**GET** `/api/products/item-code/{itemCode}`

**Access:** Public

---

### Get Products by Category

**GET** `/api/products/category/{category}`

**Access:** Public

---

### Create Product

**POST** `/api/products` (multipart/form-data)

**Access:** ADMIN

**Form Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | `String` | Yes | Product name |
| `itemCode` | `String` | Yes | Unique item code |
| `description` | `String` | No | Product description |
| `category` | `String` | Yes | Active category name |
| `price` | `BigDecimal` | Yes | Selling price (positive) |
| `costPrice` | `BigDecimal` | Yes | Cost price (positive) |
| `images` | `File[]` | No | Up to 10 product images |

```bash
curl -X POST http://localhost:8085/api/products \
  -H "Authorization: Bearer <TOKEN>" \
  -F "name=New Product" \
  -F "itemCode=NP-001" \
  -F "category=Electronics" \
  -F "price=99.99" \
  -F "costPrice=50.00" \
  -F "images=@product.jpg"
```

**Response (201):** Full product response with `imageUrls`.

---

### Update Product

**PATCH** `/api/products/{id}` (multipart/form-data)

**Access:** ADMIN

All fields are optional — only provided fields are updated.

**Form Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | `String` | No | Product name |
| `itemCode` | `String` | No | Unique item code |
| `description` | `String` | No | Product description |
| `category` | `String` | No | Active category name |
| `price` | `BigDecimal` | No | Selling price |
| `costPrice` | `BigDecimal` | No | Cost price |
| `images` | `File[]` | No | New images to **append** (up to 10 total) |
| `removeImages` | `String[]` | No | Image URLs to **delete** from S3 and DB (repeat the param for multiple) |

```bash
# Update fields, remove old image, add new image
curl -X PATCH http://localhost:8085/api/products/<UUID> \
  -H "Authorization: Bearer <TOKEN>" \
  -F "name=Updated Name" \
  -F "removeImages=https://s3.amazonaws.com/.../old-image.jpg" \
  -F "images=@new-photo.jpg"
```

**Response (200):** Updated product response.

---

### Upload Images

**POST** `/api/products/{id}/images` (multipart/form-data)

**Access:** ADMIN

Appends images to an existing product. Max 10 images total per product.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `images` | `File[]` | Yes | Images to upload |

```bash
curl -X POST http://localhost:8085/api/products/<UUID>/images \
  -H "Authorization: Bearer <TOKEN>" \
  -F "images=@photo1.jpg" \
  -F "images=@photo2.jpg"
```

**Response (200):** Updated product response with new `imageUrls`.

---

### Delete Product (Soft-Delete)

**DELETE** `/api/products/{id}`

**Access:** ADMIN

Sets `isActive = false`. This is a pure soft-delete — the product record, images, and all references remain in the database. The product is excluded from `GET /active` results and cannot be used in new orders, but past orders still show full product details.

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

### Activate Product

**PATCH** `/api/products/{id}/activate`

**Access:** ADMIN

Re-activates a previously deactivated product. Returns 400 if already active.

```bash
curl -X PATCH http://localhost:8085/api/products/<UUID>/activate \
  -H "Authorization: Bearer <TOKEN>"
```

**Success Response (200):** Full product response.

**Error Response (400):**
```json
{
  "success": false,
  "message": "Product is already active",
  "data": null
}
```

---

## Error Responses

| HTTP Status | Scenario |
|-------------|----------|
| 400 | Validation failure, duplicate item code, category not found, already active, max 10 images reached |
| 401 / 403 | Missing/invalid JWT, non-ADMIN role for mutations |
| 404 | Product not found |
| 500 | S3 upload failure, server error |

---

> Products are referenced by `itemCode` (e.g., `PA-001`) when creating orders, not by UUID.