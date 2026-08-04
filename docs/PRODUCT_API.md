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
| `maxDiscountType` | `String` | `PERCENTAGE`, `FIXED`, or `null` (no cap). Optional cap on the per-order discount an admin can apply at order time. |
| `maxDiscountValue` | `BigDecimal` | Max discount value (paired with `maxDiscountType`). When set, admin can supply a discount on each order item up to this cap. |
| `brand` | `String` | Brand name |
| `tags` | `String` | Comma-separated tags (e.g. `"premium,sport"`) |
| `specifications` | `Array` | List of `{key, value, sortOrder}` objects stored as JSONB |
| `featured` | `Boolean` | Whether product appears in featured carousel |
| `effectivePrice` | `BigDecimal` | Computed price after discount: `price * (1 - value/100)` or `price - value` |
| `totalSold` | `Long` | Total quantity sold across all orders |
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

### Get Product Stats

**GET** `/api/products/stats`

**Access:** Public

Returns aggregate product statistics across the entire database (not limited to the current page). Use this to display dashboard counts (`Total Products`, `Active`, `Low Stock`) that reflect the full catalog rather than just the current page slice.

```bash
curl http://localhost:8085/api/products/stats
```

**Response (200):**
```json
{
  "success": true,
  "message": "Product stats retrieved successfully",
  "data": {
    "total": 150,
    "active": 142,
    "lowStock": 8
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `total` | Long | Total number of products (active + inactive) |
| `active` | Long | Number of products with `isActive = true` |
| `lowStock` | Long | Number of products where `inventory.quantity <= inventory.reorderLevel` |

> All counts are full-database aggregations (`COUNT(*)`), not derived from the current page.

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

Returns all products (including inactive) matching the given category name (case-insensitive).

```bash
curl "http://localhost:8085/api/products/category/Electronics"
```

---

### Get Featured Products

**GET** `/api/products/featured`

**Access:** Public

Returns active products where `featured = true`.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | Integer | `0` | Zero-based page index |
| `size` | Integer | `8` | Page size |

```bash
curl "http://localhost:8085/api/products/featured?page=0&size=8"
```

---

### Get Deal Products

**GET** `/api/products/deals`

**Access:** Public

Returns active products that have a catalog discount set (`discountType IS NOT NULL AND discountValue > 0`).

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | Integer | `0` | Zero-based page index |
| `size` | Integer | `4` | Page size |

```bash
curl "http://localhost:8085/api/products/deals?page=0&size=4"
```

---

### Get Best Sellers

**GET** `/api/products/best-sellers`

**Access:** Public

Returns active products ranked by total quantity sold (excluding CANCELLED/RETURNED orders).

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | Integer | `0` | Zero-based page index |
| `size` | Integer | `9` | Page size |

```bash
curl "http://localhost:8085/api/products/best-sellers?page=0&size=9"
```

---

### Get New Arrivals

**GET** `/api/products/new-arrivals`

**Access:** Public

Returns active products ordered by `createdAt DESC`.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | Integer | `0` | Zero-based page index |
| `size` | Integer | `8` | Page size |

```bash
curl "http://localhost:8085/api/products/new-arrivals?page=0&size=8"
```

---

### Download Product Label

**GET** `/api/products/{id}/label`

**Access:** Public

Generates and downloads a PDF product label with name, item code, price, category, and brand.

**Response:** `application/pdf` with `Content-Disposition: attachment` header.

```bash
curl -o label.pdf "http://localhost:8085/api/products/<UUID>/label"
```

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
| `maxDiscountType` | `String` | No | `PERCENTAGE` or `FIXED`. Optional cap on per-order admin discount. |
| `maxDiscountValue` | `BigDecimal` | No | Required if `maxDiscountType` is set. Max value for per-order discount. |
| `brand` | `String` | No | Brand name (max 100 chars) |
| `tags` | `String` | No | Comma-separated tags |
| `specifications` | `String` (JSON) | No | JSON array: `[{"key":"Material","value":"Cotton","sortOrder":0}]` |
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
  -F 'specifications=[{"key":"Material","value":"Cotton","sortOrder":0}]' \
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
| `maxDiscountType` | `String` | No | `PERCENTAGE` or `FIXED`. Optional cap on per-order admin discount. |
| `maxDiscountValue` | `BigDecimal` | No | Required if `maxDiscountType` is set. Max value for per-order discount. |
| `brand` | `String` | No | Brand name |
| `tags` | `String` | No | Comma-separated tags |
| `specifications` | `String` (JSON) | No | JSON array: replaces all existing specs |
| `images` | `File[]` | No | New images to **append** (up to 10 total) |
| `removeImageUrls` | `String` | No | Comma-separated S3 URLs of images to remove |

```bash
# Update fields, add new image, remove old image
curl -X PATCH http://localhost:8085/api/products/<UUID> \
  -H "Authorization: Bearer <TOKEN>" \
  -F "name=Updated Name" \
  -F "images=@new-photo.jpg" \
  -F "removeImageUrls=https://s3.amazonaws.com/.../old.jpg"
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

### Deactivate Product

**PATCH** `/api/products/{id}/deactivate`

**Access:** ADMIN

Soft-deletes a product by setting `isActive = false`. The product is excluded from active/list results and cannot be used in new orders, but past orders retain full product details. Returns 400 if already inactive.

```bash
curl -X PATCH http://localhost:8085/api/products/<UUID>/deactivate \
  -H "Authorization: Bearer <TOKEN>"
```

**Success Response (200):**
```json
{
  "success": true,
  "message": "Product deactivated successfully",
  "data": null
}
```

---

## Inventory Endpoints (Product-Related)

These endpoints are in the InventoryController but operate on products by `productId`.

### Get Inventory by Product

**GET** `/api/inventory/product/{productId}`

**Access:** ADMIN, AGENT

Returns inventory snapshot for a product (quantity, reserved, available, reorder level, average cost).

```bash
curl "http://localhost:8085/api/inventory/product/<UUID>" \
  -H "Authorization: Bearer <TOKEN>"
```

---

### Get Stock History by Product

**GET** `/api/inventory/product/{productId}/history`

**Access:** ADMIN, AGENT

Returns paginated stock movement history sorted by `createdAt DESC`. Each entry includes `reservedQuantityBefore`/`reservedQuantityAfter` for reserve/release movements.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | Integer | `0` | Page number |
| `size` | Integer | `10` | Page size |

```bash
curl "http://localhost:8085/api/inventory/product/<UUID>/history?page=0&size=10" \
  -H "Authorization: Bearer <TOKEN>"
```

---

### Get FIFO Inventory Layers by Product

**GET** `/api/inventory/product/{productId}/layers`

**Access:** ADMIN, AGENT

Returns paginated cost layers sorted by `receivedAt ASC` (oldest first for FIFO consumption).

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | Integer | `0` | Page number |
| `size` | Integer | `10` | Page size |

```bash
curl "http://localhost:8085/api/inventory/product/<UUID>/layers?page=0&size=10" \
  -H "Authorization: Bearer <TOKEN>"
```

---

### Reserve Stock

**POST** `/api/inventory/reserve`

**Access:** ADMIN

Reserves quantity for a product (increments `reservedQuantity`). Available stock = quantity - reservedQuantity.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `productId` | UUID | Yes | Product ID |
| `quantity` | Integer | Yes | Quantity to reserve |

```bash
curl -X POST "http://localhost:8085/api/inventory/reserve?productId=<UUID>&quantity=5" \
  -H "Authorization: Bearer <TOKEN>"
```

---

### Release Reserved Stock

**POST** `/api/inventory/release`

**Access:** ADMIN

Releases previously reserved quantity (decrements `reservedQuantity`).

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `productId` | UUID | Yes | Product ID |
| `quantity` | Integer | Yes | Quantity to release |

```bash
curl -X POST "http://localhost:8085/api/inventory/release?productId=<UUID>&quantity=5" \
  -H "Authorization: Bearer <TOKEN>"
```

---

### Record Order Stock Movement

**POST** `/api/inventory/order-stock`

**Access:** ADMIN

Records a stock movement (IN, OUT, or RETURN) with FIFO layer consumption.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `productId` | UUID | Yes | Product ID |
| `quantity` | Integer | Yes | Quantity |
| `movementType` | String | Yes | `IN`, `OUT`, or `RETURN` |
| `notes` | String | No | Notes |

```bash
curl -X POST "http://localhost:8085/api/inventory/order-stock?productId=<UUID>&quantity=10&movementType=OUT&notes=Sold" \
  -H "Authorization: Bearer <TOKEN>"
```

---

### Confirm Order Stock

**POST** `/api/inventory/confirm-order-stock`

**Access:** ADMIN

Releases reserved stock AND deducts quantity (consuming FIFO layers). Records a stock movement with reserved before/after history. Used when an order is confirmed and ready for fulfillment.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `productId` | UUID | Yes | Product ID |
| `quantity` | Integer | Yes | Quantity to confirm |

```bash
curl -X POST "http://localhost:8085/api/inventory/confirm-order-stock?productId=<UUID>&quantity=5" \
  -H "Authorization: Bearer <TOKEN>"
```

---

## Report Endpoints (Product-Related)

All report endpoints require `ADMIN` role.

### Product Summary Report

**GET** `/api/reports/products/summary`

**Access:** ADMIN

```bash
curl "http://localhost:8085/api/reports/products/summary" \
  -H "Authorization: Bearer <TOKEN>"
```

**Response fields:** `totalProducts`, `activeCount`, `inactiveCount`, `categoryCount`

---

### Top Sellers Report

**GET** `/api/reports/products/top-sellers`

**Access:** ADMIN

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `period` | String | `this_month` | Period preset |
| `limit` | int | `10` | Number of results |
| `startDate` | LocalDate | null | Custom start date |
| `endDate` | LocalDate | null | Custom end date |

```bash
curl "http://localhost:8085/api/reports/products/top-sellers?period=this_month&limit=10" \
  -H "Authorization: Bearer <TOKEN>"
```

**Response fields per item:** `productId`, `productName`, `itemCode`, `category`, `quantitySold`, `revenue`, `rank`

---

### Products by Category Report

**GET** `/api/reports/products/by-category`

**Access:** ADMIN

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `period` | String | `this_month` | Period preset |
| `startDate` | LocalDate | null | Custom start date |
| `endDate` | LocalDate | null | Custom end date |

```bash
curl "http://localhost:8085/api/reports/products/by-category?period=this_month" \
  -H "Authorization: Bearer <TOKEN>"
```

**Response fields per item:** `category`, `productCount`, `quantitySold`, `revenue`

---

## Error Responses

| HTTP Status | Scenario |
|-------------|----------|
| 400 | Validation failure, duplicate item code, category not found, already active/inactive, max 10 images reached, insufficient reserved stock |
| 401 / 404 | Missing/invalid JWT, non-ADMIN role for mutations |
| 404 | Product not found, product image not found, inventory not found |
| 500 | S3 upload failure, server error |

---

> Products are referenced by `itemCode` (e.g., `PA-001`) when creating orders, not by UUID.