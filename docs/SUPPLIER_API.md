# Supplier API Reference

## Base URL

```
http://localhost:8085/api/suppliers
```

## Authentication

All endpoints require ADMIN role:

```
Authorization: Bearer <JWT_TOKEN>
```

---

## Endpoints

### Create Supplier

**POST** `/api/suppliers`

**Request body:**
```json
{
  "name": "Tech Distributors (Pvt) Ltd",
  "contactPerson": "Kamal Perera",
  "email": "kamal@techdistributors.lk",
  "phone": "+94771234567",
  "address": "42 Galle Road, Colombo 03"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | `String` | Yes | Supplier name (max 255 chars) |
| `contactPerson` | `String` | No | Contact person name |
| `email` | `String` | No | Email address |
| `phone` | `String` | No | Phone number |
| `address` | `String` | No | Physical address |

**Response (201):**
```json
{
  "success": true,
  "message": "Supplier created successfully",
  "data": {
    "id": "d1c2e3f4-5678-90ab-cdef-1234567890ab",
    "name": "Tech Distributors (Pvt) Ltd",
    "contactPerson": "Kamal Perera",
    "email": "kamal@techdistributors.lk",
    "phone": "+94771234567",
    "address": "42 Galle Road, Colombo 03",
    "active": true,
    "createdAt": "2026-07-05T16:00:00",
    "updatedAt": "2026-07-05T16:00:00"
  }
}
```

---

### Get Supplier by ID

**GET** `/api/suppliers/{id}`

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Supplier ID |

**Response (200):** Same shape as create response.

---

### List Suppliers (Paginated)

**GET** `/api/suppliers?search=tech&page=0&size=20&sort=name,asc`

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `search` | `String` | — | Optional filter by name (case-insensitive) |
| `page` | `int` | `0` | Page number (0-based) |
| `size` | `int` | `20` | Page size |
| `sort` | `String` | `name,asc` | Sort field and direction |

**Response (200):**
```json
{
  "success": true,
  "message": "Suppliers retrieved successfully",
  "data": {
    "content": [ { ... supplier objects ... } ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

---

### Update Supplier

**PUT** `/api/suppliers/{id}`

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Supplier ID |

**Request body:** Same as create — all fields optional (only provided fields are updated).

**Response (200):** Returns updated supplier object.

---

### Deactivate Supplier

**PATCH** `/api/suppliers/{id}/deactivate`

Soft-deletes the supplier (sets `active = false`). Suppliers are not hard-deleted.

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Supplier ID |

**Response (200):**
```json
{
  "success": true,
  "message": "Supplier deactivated successfully",
  "data": null
}
```

---

## Error Responses

| Code | Scenario |
|------|----------|
| 400 | Validation failed (e.g. blank name, email format) |
| 404 | Supplier not found |
| 409 | Duplicate supplier name |
