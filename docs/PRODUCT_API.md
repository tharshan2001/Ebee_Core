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
| `discountType` | `String` | `PERCENTAGE`, `FIXED`, or `null` (no discount) |
| `discountValue` | `BigDecimal` | Discount value (percentage or fixed amount) |
| `brand` | `String` | Brand name |
| `tags` | `String` | Comma-separated tags (e.g. `"premium,sport"`) |
| `effectivePrice` | `BigDecimal` | Computed price after discount: `price * (1 - value/100)` or `price - value` |
| `imageUrls` | `String[]` | S3 URLs of product images |
| `images` | `ProductImageResponse[]` | Structured image data with IDs, URLs, names, types, and sortOrder |
| `inventory` | `Object` | Inventory snapshot (`availableQuantity`, `isLowStock`) |
| `createdAt` | `LocalDateTime` | Creation timestamp |
| `updatedAt` | `LocalDateTime` | Last update timestamp |

---

## Endpoints

### Search Products (Non-Paginated)

**GET** `/api/products/search?q=...`

**Access:** Public

Returns **all** matching active products as a flat array. No pagination — useful for autocomplete dropdowns and typeahead searches.

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `q` | String | yes | Search query (matches name, itemCode, or category) |

```bash
curl "http://localhost:8085/api/products/search?q=Test"
```

**Response (200):**
```json
{
  "success": true,
  "message": "Products retrieved successfully",
  "data": [
    {
      "id": "uuid",
      "name": "Test Product",
      "itemCode": "TEST-001",
      "category": "Electronics",
      "price": 59.99,
      "isActive": true,
      "imageUrls": [],
      "inventory": {
        "availableQuantity": 100,
        "isLowStock": false
      }
    }
  ]
}
```

`data` is a **flat array** (not a `PageResponse` wrapper). Each item has the same shape as the paginated endpoint items.

Only **active** products are returned.

---

### Get Active Products (Paginated)

**GET** `/api/products/active`

**Access:** Public

Returns active products (`isActive = true`) with pagination. These are the only products available for new orders.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | Integer | `0` | Zero-based page index |
| `size` | Integer | `20` | Page size (max 100) |
| `sort` | String | `createdAt,desc` | Format: `field,direction` |

```bash
# Get active products, page 0, 20 items
curl "http://localhost:8085/api/products/active?page=0&size=20"
```

**Response:**
```json
{
  "success": true,
  "message": "Active products retrieved successfully",
  "data": {
    "content": [
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
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 45,
    "totalPages": 3,
    "first": true,
    "last": false
  }
}
```

`data` is a `PageResponse` wrapper (same shape as "Get All Products" below).

---

### Get All Products (Paginated)

**GET** `/api/products`

**Access:** Public

Returns all products (active and inactive) with pagination, sorting, and search support.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | Integer | `0` | Zero-based page index |
| `size` | Integer | `20` | Page size (max 100) |
| `sort` | String | `createdAt,desc` | Format: `field,direction` (e.g., `name,asc`, `price,desc`) |
| `search` | String | — | Search term (matches name, itemCode, or category) |

```bash
# Get page 0 with 20 items, sorted by name ascending
curl "http://localhost:8085/api/products?page=0&size=20&sort=name,asc"

# Search for "Test" in name, itemCode, or category
curl "http://localhost:8085/api/products?search=Test&size=10"
```

**Response (Paginated):**
```json
{
  "success": true,
  "message": "Products retrieved successfully",
  "data": {
    "content": [
      {
        "id": "171ab477-cba6-4589-934d-01906d480852",
        "name": "Updated Product A",
        "itemCode": "PA-001",
        "category": "Electronics",
        "price": 59.99,
        "isActive": true,
        "imageUrls": [...],
        "inventory": { "availableQuantity": 141, "isLowStock": false }
      }
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 150,
    "totalPages": 8,
    "first": true,
    "last": false
  }
}
```

**Response Fields (PageResponse):**
| Field | Type | Description |
|-------|------|-------------|
| `content` | Array | Array of product objects |
| `pageNumber` | Integer | Current page (zero-based) |
| `pageSize` | Integer | Items per page |
| `totalElements` | Long | Total matching records |
| `totalPages` | Integer | Total pages available |
| `first` | Boolean | Whether this is the first page |
| `last` | Boolean | Whether this is the last page |

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
| `discountType` | `String` | No | `PERCENTAGE` or `FIXED` |
| `discountValue` | `BigDecimal` | No | Required if `discountType` is set |
| `brand` | `String` | No | Brand name (max 100 chars) |
| `tags` | `String` | No | Comma-separated tags |
| `images` | `File[]` | No | Up to 10 product images |

```bash
curl -X POST http://localhost:8085/api/products \
  -H "Authorization: Bearer <TOKEN>" \
  -F "name=New Product" \
  -F "itemCode=NP-001" \
  -F "category=Electronics" \
  -F "price=99.99" \
  -F "costPrice=50.00" \
  -F "discountType=PERCENTAGE" \
  -F "discountValue=10" \
  -F "brand=Nike" \
  -F "tags=premium,sport" \
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
| `discountType` | `String` | No | `PERCENTAGE` or `FIXED` (set to clear, omit to leave unchanged) |
| `discountValue` | `BigDecimal` | No | Required if `discountType` is set |
| `brand` | `String` | No | Brand name |
| `tags` | `String` | No | Comma-separated tags |
| `images` | `File[]` | No | New images to **append** (up to 10 total) |

```bash
# Update fields, add new image
curl -X PATCH http://localhost:8085/api/products/<UUID> \
  -H "Authorization: Bearer <TOKEN>" \
  -F "name=Updated Name" \
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

### Delete Product Image

**DELETE** `/api/products/{productId}/images/{imageId}`

**Access:** ADMIN

Deletes a **single** product image by its UUID. This provides fine-grained control over product images, replacing the bulk `removeImages` approach.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `productId` | `UUID` | Path Variable | ID of the product |
| `imageId` | `UUID` | Path Variable | ID of the image to delete (must exist and belong to the product) |

**Form Fields:**

```bash
curl -X DELETE http://localhost:8085/api/products/<PRODUCT_UUID>/<IMAGE_UUID> \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

**Response (200):** Success message confirming deletion.

**Behavior:**
- Deletes the image from S3 storage
- Removes the record from the `product_images` database table
- Removes the image from the `images` field in the response (while keeping `imageUrls` for backward compatibility)
- Fails with `404` if the image doesn't exist or doesn't belong to the product
- Removes the `removeImages` parameter from the PATCH `/api/products/{id}` endpoint (legacy bulk removal is deprecated)

**Response:**
```json
{
  "success": true,
  "message": "Product image deleted successfully",
  "data": null
}
```

**Error Response (404):**
```json
{
  "success": false,
  "message": "ProductImage not found",
  "data": null
}
```

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

### ProductImageResponse

Structure used in the `images` field of Product responses:

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Unique image ID |
| `imageUrl` | `String` | S3 URL of the image |
| `imageName` | `String` | Original filename |
| `imageType` | `String` | MIME type (e.g. `image/jpeg`) |
| `sortOrder` | `Integer` | Display order (0 for primary) |

**Example:**
```json
"images": [
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "imageUrl": "https://s3.amazonaws.com/.../product1-001.jpg",
    "imageName": "product1-001.jpg",
    "imageType": "image/jpeg",
    "sortOrder": 0
  },
  {
    "id": "550e8400-e29b-41d4-a716-446655441111",
    "imageUrl": "https://s3.amazonaws.com/.../product1-002.jpg",
    "imageName": "product1-002.jpg",
    "imageType": "image/png",
    "sortOrder": 1
  }
]
```

The `images` field provides structured image metadata for frontend targeting, while `imageUrls` (String array) remains for backward compatibility. Use `DELETE /api/products/{productId}/images/{imageId}` for targeted deletion.

---

### Error Responses

| HTTP Status | Scenario |
|-------------|----------|
| 400 | Validation failure, duplicate item code, category not found, already active, max 10 images reached |
| 401 / 403 | Missing/invalid JWT, non-ADMIN role for mutations |
| 404 | Product not found, product image not found |
| 500 | S3 upload failure, server error |

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