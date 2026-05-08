# Ebee POS API Documentation

## Base URL
```
http://localhost:8085/api
```

## Authentication
All endpoints (except login/register) require JWT token in header:
```
Authorization: Bearer <token>
```

---

# 1. Products

## Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/products` | Create product |
| GET | `/products` | Get all products |
| GET | `/products/active` | Get active products |
| GET | `/products/{id}` | Get product by ID |
| GET | `/products/sku/{sku}` | Get product by SKU |
| PATCH | `/products/{id}` | Update product |
| POST | `/products/{id}/images` | Upload images |
| DELETE | `/products/{id}` | Soft delete product |

---

## Create Product
**POST** `/products`

### Request (multipart/form-data)
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| name | string | yes | Product name |
| sku | string | yes | Unique SKU |
| description | string | no | Product description |
| category | string | yes | Product category |
| price | number | yes | Selling price |
| costPrice | number | yes | Cost price |
| images | file[] | no | Product images (max 10) |

### Response
```json
{
  "success": true,
  "message": "Product created successfully",
  "data": {
    "id": "746f35da-dbee-4e2b-a7ab-23fcc4e79a13",
    "name": "Browser",
    "sku": "BRW001",
    "description": null,
    "category": "Electronics",
    "price": 250.00,
    "costPrice": 150.00,
    "isActive": true,
    "imageUrls": [],
    "inventory": {
      "id": "4b2fbacb-1897-45f7-810d-06b4a2186f87",
      "productId": "746f35da-dbee-4e2b-a7ab-23fcc4e79a13",
      "productName": "Browser",
      "productSku": "BRW001",
      "quantity": 50,
      "reservedQuantity": 0,
      "availableQuantity": 50,
      "reorderLevel": null,
      "isLowStock": false,
      "createdAt": "2026-05-08T20:38:55.890176",
      "updatedAt": "2026-05-08T20:38:55.890176"
    },
    "createdAt": "2026-05-08T20:38:55.123456",
    "updatedAt": "2026-05-08T20:38:55.123456"
  }
}
```

---

## Get All Products
**GET** `/products`

### Response
```json
{
  "success": true,
  "message": "Products retrieved successfully",
  "data": [
    {
      "id": "...",
      "name": "Product Name",
      "sku": "SKU001",
      "price": 100.00,
      "costPrice": 50.00,
      "inventory": {
        "quantity": 100,
        "availableQuantity": 100,
        "isLowStock": false
      }
    }
  ]
}
```

---

## Get Product by ID
**GET** `/products/{id}`

### Response
```json
{
  "success": true,
  "message": "Product retrieved successfully",
  "data": {
    "id": "746f35da-dbee-4e2b-a7ab-23fcc4e79a13",
    "name": "Browser",
    "sku": "BRW001",
    "description": "Product description",
    "category": "Electronics",
    "price": 250.00,
    "costPrice": 150.00,
    "isActive": true,
    "imageUrls": ["https://s3.../image1.jpg"],
    "inventory": {
      "id": "4b2fbacb-1897-45f7-810d-06b4a2186f87",
      "productId": "746f35da-dbee-4e2b-a7ab-23fcc4e79a13",
      "productName": "Browser",
      "productSku": "BRW001",
      "quantity": 50,
      "reservedQuantity": 0,
      "availableQuantity": 50,
      "reorderLevel": 10,
      "isLowStock": false,
      "createdAt": "2026-05-08T20:38:55.890176",
      "updatedAt": "2026-05-08T20:38:55.890176"
    },
    "createdAt": "2026-05-08T20:38:55.123456",
    "updatedAt": "2026-05-08T20:38:55.123456"
  }
}
```

---

## Update Product
**PATCH** `/products/{id}`

### Request (multipart/form-data)
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| name | string | no | Product name |
| sku | string | no | Unique SKU |
| description | string | no | Product description |
| category | string | no | Product category |
| price | number | no | Selling price |
| costPrice | number | no | Cost price |
| images | file[] | no | Add more images |

### Response
```json
{
  "success": true,
  "message": "Product updated successfully",
  "data": { ... }
}
```

---

## Upload Product Images
**POST** `/products/{id}/images`

### Request (multipart/form-data)
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| images | file[] | yes | Images to upload (max 10) |

### Response
```json
{
  "success": true,
  "message": "Images uploaded successfully",
  "data": {
    "id": "...",
    "imageUrls": [
      "https://s3.../image1.jpg",
      "https://s3.../image2.jpg"
    ]
  }
}
```

---

## Delete Product (Soft Delete)
**DELETE** `/products/{id}`

### Response
```json
{
  "success": true,
  "message": "Product deleted successfully"
}
```

---

# 2. Inventory

## Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/inventory/{id}` | Get inventory by ID |
| GET | `/inventory/product/{productId}` | Get inventory by product |
| GET | `/inventory` | Get all inventories |
| GET | `/inventory/low-stock` | Get low stock items |
| POST | `/inventory/adjust` | Adjust stock |
| POST | `/inventory/reserve` | Reserve stock |
| POST | `/inventory/release` | Release reserved stock |
| GET | `/inventory/product/{productId}/history` | Get stock history |

---

## Get Inventory by Product
**GET** `/inventory/product/{productId}`

### Response
```json
{
  "success": true,
  "message": "Inventory retrieved successfully",
  "data": {
    "id": "4b2fbacb-1897-45f7-810d-06b4a2186f87",
    "productId": "746f35da-dbee-4e2b-a7ab-23fcc4e79a13",
    "productName": "Browser",
    "productSku": "BRW001",
    "quantity": 50,
    "reservedQuantity": 5,
    "availableQuantity": 45,
    "reorderLevel": 10,
    "isLowStock": false,
    "createdAt": "2026-05-08T20:38:55.890176",
    "updatedAt": "2026-05-08T20:40:00.000000"
  }
}
```

---

## Get All Inventories
**GET** `/inventory`

### Response
```json
{
  "success": true,
  "message": "Inventories retrieved successfully",
  "data": [
    {
      "id": "...",
      "productId": "...",
      "productName": "Product 1",
      "quantity": 100,
      "availableQuantity": 100,
      "isLowStock": false
    }
  ]
}
```

---

## Get Low Stock Items
**GET** `/inventory/low-stock`

### Response
```json
{
  "success": true,
  "message": "Low stock inventories retrieved successfully",
  "data": [
    {
      "id": "...",
      "productId": "...",
      "productName": "Low Stock Product",
      "quantity": 5,
      "availableQuantity": 5,
      "reorderLevel": 10,
      "isLowStock": true
    }
  ]
}
```

---

## Adjust Stock (IN/OUT/ADJUSTMENT)
**POST** `/inventory/adjust`

### Request (application/json)
```json
{
  "productId": "746f35da-dbee-4e2b-a7ab-23fcc4e79a13",
  "quantityChange": 100,
  "movementType": "IN",
  "notes": "Stock replenishment"
}
```

### Movement Types
| Type | Description |
|------|-------------|
| IN | Add stock (purchase, return) |
| OUT | Remove stock (sold, damaged) |
| ADJUSTMENT | Manual adjustment |
| RETURN | Customer return |
| DAMAGE | Damaged goods |

### Response
```json
{
  "success": true,
  "message": "Stock adjusted successfully",
  "data": {
    "id": "4b2fbacb-1897-45f7-810d-06b4a2186f87",
    "productId": "746f35da-dbee-4e2b-a7ab-23fcc4e79a13",
    "quantity": 150,
    "availableQuantity": 150,
    "isLowStock": false
  }
}
```

---

## Reserve Stock
**POST** `/inventory/reserve`

### Request
```
productId: UUID
quantity: number
```

### Response
```json
{
  "success": true,
  "message": "Stock reserved successfully",
  "data": {
    "quantity": 50,
    "reservedQuantity": 10,
    "availableQuantity": 40
  }
}
```

---

## Release Reserved Stock
**POST** `/inventory/release`

### Request
```
productId: UUID
quantity: number
```

### Response
```json
{
  "success": true,
  "message": "Reserved stock released successfully",
  "data": {
    "quantity": 50,
    "reservedQuantity": 5,
    "availableQuantity": 45
  }
}
```

---

## Get Stock History
**GET** `/inventory/product/{productId}/history`

### Response
```json
{
  "success": true,
  "message": "Stock history retrieved successfully",
  "data": [
    {
      "id": "ec91f158-835d-4f5a-9c87-a99f01b01df1",
      "productId": "746f35da-dbee-4e2b-a7ab-23fcc4e79a13",
      "productName": "Browser",
      "productSku": "BRW001",
      "quantityChange": 50,
      "quantityBefore": 0,
      "quantityAfter": 50,
      "movementType": "IN",
      "notes": "Initial stock",
      "createdById": "5b718629-785e-4adf-a475-01e7c5957670",
      "createdByName": "Admin User",
      "createdByEmail": "admin@ebee.com",
      "createdAt": "2026-05-08T20:38:55.890176"
    }
  ]
}
```

---

# 3. TypeScript Interfaces

```typescript
// ============================================
// Product Types
// ============================================

export interface ProductRequest {
  name: string;
  sku: string;
  description?: string;
  category: string;
  price: number;
  costPrice: number;
  images?: File[];
}

export interface ProductResponse {
  id: string;
  name: string;
  sku: string;
  description: string | null;
  category: string;
  price: number;
  costPrice: number;
  isActive: boolean;
  imageUrls: string[];
  inventory: InventoryResponse | null;
  createdAt: string;
  updatedAt: string;
}

// ============================================
// Inventory Types
// ============================================

export interface InventoryResponse {
  id: string;
  productId: string;
  productName: string;
  productSku: string;
  quantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  reorderLevel: number | null;
  isLowStock: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface StockAdjustRequest {
  productId: string;
  quantityChange: number;
  movementType: 'IN' | 'OUT' | 'ADJUSTMENT' | 'RETURN' | 'DAMAGE';
  notes?: string;
}

export interface StockReserveRequest {
  productId: string;
  quantity: number;
}

// ============================================
// Stock Movement Types
// ============================================

export interface StockMovementResponse {
  id: string;
  productId: string;
  productName: string;
  productSku: string;
  quantityChange: number;
  quantityBefore: number;
  quantityAfter: number;
  movementType: 'IN' | 'OUT' | 'ADJUSTMENT' | 'RETURN' | 'DAMAGE';
  notes: string | null;
  createdById: string;
  createdByName: string;
  createdByEmail: string;
  createdAt: string;
}

// ============================================
// API Response Wrapper
// ============================================

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
}
```

---

# 4. Frontend Usage Examples

## 4.1 Create Product

```typescript
import axios from 'axios';

const API_URL = 'http://localhost:8085/api';

const api = axios.create({
  baseURL: API_URL,
});

// Add token to requests
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

async function createProduct(productData: {
  name: string;
  sku: string;
  category: string;
  price: number;
  costPrice: number;
  images?: File[];
}) {
  const formData = new FormData();
  formData.append('name', productData.name);
  formData.append('sku', productData.sku);
  formData.append('category', productData.category);
  formData.append('price', productData.price.toString());
  formData.append('costPrice', productData.costPrice.toString());
  
  if (productData.images) {
    productData.images.forEach((image) => {
      formData.append('images', image);
    });
  }

  const response = await api.post('/products', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}
```

## 4.2 Get Products with Inventory

```typescript
async function getProducts() {
  const response = await api.get('/products');
  return response.data;
}

// Usage
const result = await getProducts();
// result.data = ProductResponse[]
```

## 4.3 Adjust Stock

```typescript
async function adjustStock(productId: string, quantity: number, type: 'IN' | 'OUT', notes?: string) {
  const response = await api.post('/inventory/adjust', {
    productId,
    quantityChange: quantity,
    movementType: type,
    notes,
  });
  return response.data;
}

// Usage - Add 100 units
await adjustStock('746f35da-dbee-4e2b-a7ab-23fcc4e79a13', 100, 'IN', 'Stock replenishment');

// Usage - Remove 10 units
await adjustStock('746f35da-dbee-4e2b-a7ab-23fcc4e79a13', 10, 'OUT', 'Sold to customer');
```

## 4.4 Get Low Stock Products

```typescript
async function getLowStockProducts() {
  const response = await api.get('/inventory/low-stock');
  return response.data.data; // Returns array of InventoryResponse where isLowStock = true
}
```

## 4.5 Get Stock History

```typescript
async function getStockHistory(productId: string) {
  const response = await api.get(`/inventory/product/${productId}/history`);
  return response.data.data;
}

// Returns array of stock movements with:
// - quantityChange (positive for IN, negative for OUT)
// - movementType
// - notes
// - createdByName
// - createdAt
```

## 4.6 Check Stock Availability for Order

```typescript
async function checkStockAvailable(productId: string, quantity: number): Promise<boolean> {
  const response = await api.get(`/inventory/product/${productId}`);
  const inventory = response.data.data;
  return inventory.availableQuantity >= quantity;
}

// Usage
const canOrder = await checkStockAvailable('746f35da-dbee-4e2b-a7ab-23fcc4e79a13', 10);
if (!canOrder) {
  alert('Insufficient stock!');
}
```

---

# 5. Complete Workflow

## 5.1 Product Creation → Stock Management

```typescript
// 1. Create Product (Inventory created automatically with qty: 0)
const product = await createProduct({
  name: 'Wireless Mouse',
  sku: 'WM-001',
  category: 'Electronics',
  price: 25.00,
  costPrice: 15.00,
});
// product.id = "xxx"
// product.inventory.id = "yyy"
// product.inventory.quantity = 0

// 2. Add initial stock
await adjustStock(product.id, 100, 'IN', 'Initial stock received');

// 3. Verify
const updated = await api.get(`/products/${product.id}`);
console.log(updated.data.inventory.quantity); // 100
console.log(updated.data.inventory.availableQuantity); // 100
```

## 5.2 Order Processing

```typescript
async function processOrder(orderItems: { productId: string; quantity: number }[]) {
  // 1. Check stock for each item
  for (const item of orderItems) {
    const hasStock = await checkStockAvailable(item.productId, item.quantity);
    if (!hasStock) {
      throw new Error(`Insufficient stock for product ${item.productId}`);
    }
  }

  // 2. Deduct stock for each item
  for (const item of orderItems) {
    await adjustStock(item.productId, item.quantity, 'OUT', `Order ORD-001`);
  }
}
```

## 5.3 Order Cancellation (Restore Stock)

```typescript
async function cancelOrder(orderId: string, orderItems: { productId: string; quantity: number }[]) {
  for (const item of orderItems) {
    await adjustStock(item.productId, item.quantity, 'RETURN', `Order ${orderId} cancelled`);
  }
}
```

---

# 6. Error Handling

```typescript
try {
  const response = await api.post('/products', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  
  if (response.data.success) {
    console.log('Product created:', response.data.data);
  }
} catch (error: any) {
  if (error.response) {
    // Server responded with error
    console.error('Error:', error.response.data.message);
    
    // Common error codes:
    // - 400: Bad request (validation error)
    // - 401: Unauthorized (invalid token)
    // - 403: Forbidden (no permission)
    // - 404: Not found
    // - 500: Internal server error
  } else if (error.request) {
    // Request made but no response
    console.error('Network error');
  } else {
    console.error('Error:', error.message);
  }
}
```

---

# 7. Common Response Examples

## Success Response
```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { ... }
}
```

## Error Response
```json
{
  "success": false,
  "message": "Error description here",
  "data": null
}
```

## Not Found Response
```json
{
  "success": false,
  "message": "Product not found with id: 'xxx'",
  "data": null
}
```

## Validation Error Response
```json
{
  "success": false,
  "message": "SKU already exists: SKU001",
  "data": null
}
```

---

# 8. Testing with cURL

```bash
# Login
TOKEN=$(curl -s -X POST http://localhost:8085/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@ebee.com","password":"admin123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# Create Product
curl -X POST http://localhost:8085/api/products \
  -H "Authorization: Bearer $TOKEN" \
  -F "name=Test Product" \
  -F "sku=TEST-001" \
  -F "category=Electronics" \
  -F "price=100.00" \
  -F "costPrice=50.00"

# Get Products
curl http://localhost:8085/api/products \
  -H "Authorization: Bearer $TOKEN"

# Add Stock
curl -X POST http://localhost:8085/api/inventory/adjust \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"productId":"<PRODUCT_ID>","quantityChange":100,"movementType":"IN","notes":"Initial stock"}'

# Get Inventory
curl http://localhost:8085/api/inventory/product/<PRODUCT_ID> \
  -H "Authorization: Bearer $TOKEN"

# Get Low Stock
curl http://localhost:8085/api/inventory/low-stock \
  -H "Authorization: Bearer $TOKEN"

# Get Stock History
curl http://localhost:8085/api/inventory/product/<PRODUCT_ID>/history \
  -H "Authorization: Bearer $TOKEN"
```

---

# 9. Notes

1. **Inventory is auto-created** when a product is created (quantity starts at 0)
2. **Stock must be added** using `/inventory/adju` endpoint after product creation
3. **Available Quantity** = Quantity - Reserved Quantity
4. **Low Stock** = When available quantity <= reorder level
5. **Reserved Stock** is held for cart/checkout processes
6. **Orders automatically deduct stock** when created
7. **Order cancellation restores stock** automatically