# Customer API Documentation

## Base URL
```
http://localhost:8085/api/customers
```

## Authentication
All customer endpoints require JWT authentication via Authorization header:
```
Authorization: Bearer <JWT_TOKEN>
```

### Roles
- `ADMIN` - Full access
- `AGENT` - Full access

---

## Endpoints

### 1. Create Customer
**POST** `/api/customers`

Creates a new customer. Phone number must be unique.

**Access:** ADMIN, AGENT

**Request Headers:**
```
Content-Type: application/json
Authorization: Bearer <JWT_TOKEN>
```

**Request Body:**
```json
{
  "firstName": "Arun",
  "lastName": "Kumar",
  "phoneNumber": "+94111111111",
  "email": "arun@example.com"
}
```

**Validation:**
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| firstName | String | Yes | |
| lastName | String | Yes | |
| phoneNumber | String | Yes | Must be unique |
| email | String | No | |

**Success Response (201 Created):**
```json
{
  "success": true,
  "message": "Customer created successfully",
  "data": {
    "id": "16cd9e90-e123-40e1-91a5-d2f00ed0add7",
    "firstName": "Arun",
    "lastName": "Kumar",
    "phoneNumber": "+94111111111",
    "email": "arun@example.com",
    "totalOrders": 0,
    "createdAt": "2026-05-30T11:50:43",
    "updatedAt": "2026-05-30T11:50:43"
  }
}
```

**Error Responses:**
- 400: Customer already exists with phone
- 401: Unauthorized
- 403: Forbidden

---

### 2. Get Customer by ID
**GET** `/api/customers/{id}`

Retrieves a customer by UUID.

**Access:** ADMIN, AGENT

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| id | UUID | Customer ID |

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Customer retrieved successfully",
  "data": {
    "id": "16cd9e90-e123-40e1-91a5-d2f00ed0add7",
    "firstName": "Arun",
    "lastName": "Kumar",
    "phoneNumber": "+94111111111",
    "email": "arun@example.com",
    "totalOrders": 5,
    "createdAt": "2026-05-30T11:50:43",
    "updatedAt": "2026-05-30T14:23:50"
  }
}
```

**Error Responses:**
- 404: Customer not found

---

### 3. Get Customer by Phone
**GET** `/api/customers/phone/{phoneNumber}`

Retrieves a customer by phone number.

**Access:** ADMIN, AGENT

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| phoneNumber | String | Phone number (URL-encoded if needed, e.g. `%2B` for `+`) |

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Customer retrieved successfully",
  "data": { ... }
}
```

**Error Responses:**
- 404: Customer not found with phoneNumber

---

### 4. List All Customers
**GET** `/api/customers`

Retrieves all customers.

**Access:** ADMIN, AGENT

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Customers retrieved successfully",
  "data": [
    { ... },
    { ... }
  ]
}
```

---

## Data Models

### CustomerRequest
```json
{
  "firstName": "String (required)",
  "lastName": "String (required)",
  "phoneNumber": "String (required, unique)",
  "email": "String (optional)"
}
```

### CustomerResponse
```json
{
  "id": "UUID",
  "firstName": "String",
  "lastName": "String",
  "phoneNumber": "String",
  "email": "String or null",
  "totalOrders": "Integer",
  "createdAt": "LocalDateTime",
  "updatedAt": "LocalDateTime"
}
```

---

## Examples

### Create and Lookup Customer
```bash
# Login
TOKEN=$(curl -s -X POST http://localhost:8085/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@ebee.com","password":"admin123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# Create customer
curl -X POST http://localhost:8085/api/customers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Ranil",
    "lastName": "Wickramasinghe",
    "phoneNumber": "+94771234567"
  }'

# Lookup by phone
curl http://localhost:8085/api/customers/phone/%2B94771234567 \
  -H "Authorization: Bearer $TOKEN"

# List all customers
curl http://localhost:8085/api/customers \
  -H "Authorization: Bearer $TOKEN"
```

---

## Error Handling

All errors follow the standard ApiResponse format:
```json
{
  "success": false,
  "message": "Error description",
  "data": null
}
```

**Common Error Codes:**
| Status Code | Description |
|-------------|-------------|
| 400 | Bad Request - Invalid input / Duplicate phone |
| 401 | Unauthorized - Invalid/missing token |
| 403 | Forbidden - Insufficient permissions |
| 404 | Not Found - Resource not found |
| 500 | Internal Server Error |
