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
- `ADMIN` — Full access
- `AGENT` — Full access

---

## Address

Each customer can have one address managed as a nested object within the customer. Addresses are created and updated through the customer endpoints — there is no separate address CRUD.

### Address Fields

| Field | Type | Max | Notes |
|-------|------|-----|-------|
| street | String | 255 | |
| city | String | 100 | |
| district | String | — | Must be one of the supported districts (see [Agent API](AGENT_API.md#supported-districts)) |
| postalCode | String | 20 | |

`district` is validated against the enum — invalid values return 400 with `"Invalid district: ..."`. Include the `address` object in the JSON body to set it. Omit it (or set `null`) to leave the customer without an address.

---

## Endpoints

### 1. Create Customer

**POST** `/api/customers`

Creates a new customer. Phone number must be unique.

**Access:** ADMIN, AGENT

**Request Body:**
```json
{
  "firstName": "Arun",
  "lastName": "Kumar",
  "phoneNumber": "+94111111111",
  "email": "arun@example.com",
  "address": {
    "street": "123 Main St",
    "city": "Jaffna",
    "district": "Jaffna",
    "postalCode": "40000"
  }
}
```

**Validation:**

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| firstName | String | Yes | |
| lastName | String | Yes | |
| phoneNumber | String | Yes | Must be unique |
| email | String | No | |
| address | Object | No | Nested address object |
| address.street | String | No | Max 255 chars |
| address.city | String | No | Max 100 chars |
| address.district | String | No | Must be a supported district (see table in [Agent API](AGENT_API.md#supported-districts)), case-insensitive |
| address.postalCode | String | No | Max 20 chars |

**Success Response (201 Created):**
```json
{
  "success": true,
  "message": "Customer created successfully",
  "data": {
    "id": "2465d540-dcfa-443f-93c8-d5b3efbc0cc8",
    "firstName": "Arun",
    "lastName": "Kumar",
    "phoneNumber": "+94111111111",
    "email": "arun@example.com",
    "totalOrders": 0,
    "address": {
      "id": "ae899298-48ef-4bb2-8e92-02d77a8995aa",
      "street": "123 Main St",
      "city": "Jaffna",
      "district": "Jaffna",
      "postalCode": "40000"
    },
    "createdAt": "2026-06-03T23:58:55",
    "updatedAt": "2026-06-03T23:58:55"
  }
}
```

**Error Responses:**
- `400` — Customer already exists with phone
- `401` — Unauthorized
- `403` — Forbidden

---

### 2. Update Customer

**PUT** `/api/customers/{id}`

Updates an existing customer. All fields are optional (only provided fields are updated). Phone number uniqueness is enforced if changed.

**Access:** ADMIN, AGENT

**Request Body:**
```json
{
  "firstName": "Arun",
  "lastName": "Kumar",
  "phoneNumber": "+94111111111",
  "email": "newemail@example.com",
  "address": {
    "street": "456 New Rd",
    "city": "Batticaloa City",
    "district": "Batticaloa",
    "postalCode": "30000"
  }
}
```

If the customer already has an address, it is **updated in place**. If they don't have one, a new address is **created**. To remove an address, send `"address": null`.

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Customer updated successfully",
  "data": {
    "id": "2465d540-dcfa-443f-93c8-d5b3efbc0cc8",
    "firstName": "Arun",
    "lastName": "Kumar",
    "phoneNumber": "+94111111111",
    "email": "newemail@example.com",
    "totalOrders": 0,
    "address": {
      "id": "ae899298-48ef-4bb2-8e92-02d77a8995aa",
      "street": "456 New Rd",
      "city": "Batticaloa City",
      "district": "Batticaloa",
      "postalCode": "30000"
    },
    "createdAt": "2026-06-03T23:58:55",
    "updatedAt": "2026-06-03T23:59:10"
  }
}
```

**Error Responses:**
- `400` — Customer already exists with phone
- `404` — Customer not found

---

### 3. Get Customer by ID

**GET** `/api/customers/{id}`

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
    "id": "2465d540-dcfa-443f-93c8-d5b3efbc0cc8",
    "firstName": "Arun",
    "lastName": "Kumar",
    "phoneNumber": "+94111111111",
    "email": "arun@example.com",
    "totalOrders": 5,
    "address": {
      "id": "ae899298-48ef-4bb2-8e92-02d77a8995aa",
      "street": "123 Main St",
      "city": "Jaffna",
      "district": "Jaffna",
      "postalCode": "40000"
    },
    "createdAt": "2026-06-03T23:58:55",
    "updatedAt": "2026-06-03T23:59:10"
  }
}
```

**Error Responses:**
- `404` — Customer not found

---

### 4. Get Customer by Phone

**GET** `/api/customers/phone/{phoneNumber}`

**Access:** ADMIN, AGENT

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| phoneNumber | String | Phone number (URL-encode `+` as `%2B`) |

**Success Response (200 OK):** Same shape as Get by ID.

**Error Responses:**
- `404` — Customer not found with phoneNumber

---

### 5. List All Customers

**GET** `/api/customers`

**Access:** ADMIN, AGENT

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Customers retrieved successfully",
  "data": [
    {
      "id": "2465d540-dcfa-443f-93c8-d5b3efbc0cc8",
      "firstName": "Arun",
      "lastName": "Kumar",
      "phoneNumber": "+94111111111",
      "email": "arun@example.com",
      "totalOrders": 0,
      "address": {
        "id": "ae899298-48ef-4bb2-8e92-02d77a8995aa",
        "street": "123 Main St",
        "city": "Jaffna",
        "district": "Jaffna",
        "postalCode": "40000"
      },
      "createdAt": "2026-06-03T23:58:55",
      "updatedAt": "2026-06-03T23:58:55"
    },
    {
      "id": "7bc5d61f-f4d4-4154-be94-5bedbfc41416",
      "firstName": "Jane",
      "lastName": "Smith",
      "phoneNumber": "+94771234598",
      "email": null,
      "totalOrders": 0,
      "address": null,
      "createdAt": "2026-06-03T23:59:00",
      "updatedAt": "2026-06-03T23:59:00"
    }
  ]
}
```

---

## Data Models

### AddressRequest
```json
{
  "street": "String (optional, max 255)",
  "city": "String (optional, max 100)",
  "district": "String (optional, max 100)",
  "postalCode": "String (optional, max 20)"
}
```

### AddressResponse
```json
{
  "id": "UUID",
  "street": "String or null",
  "city": "String or null",
  "district": "String or null",
  "postalCode": "String or null"
}
```

### CustomerRequest
```json
{
  "firstName": "String (required)",
  "lastName": "String (required)",
  "phoneNumber": "String (required, unique)",
  "email": "String (optional)",
  "address": "AddressRequest (optional)"
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
  "address": "AddressResponse or null",
  "createdAt": "LocalDateTime",
  "updatedAt": "LocalDateTime"
}
```

---

## Examples

### Create, Update, and Lookup Customer

```bash
# Login
TOKEN=$(curl -s -X POST http://localhost:8085/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@ebee.com","password":"admin123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# Create customer with address
curl -X POST http://localhost:8085/api/customers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Ranil",
    "lastName": "Wickramasinghe",
    "phoneNumber": "+94771234567",
    "address": {
      "street": "123 Main St",
      "city": "Jaffna",
      "district": "Jaffna",
      "postalCode": "40000"
    }
  }'

# Update address
ID=$(curl -s http://localhost:8085/api/customers \
  -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
curl -X PUT "http://localhost:8085/api/customers/$ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Ranil",
    "lastName": "Wickramasinghe",
    "phoneNumber": "+94771234567",
    "address": {
      "street": "456 New Rd",
      "city": "Batticaloa City",
      "district": "Batticaloa",
      "postalCode": "30000"
    }
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

| Status | Description |
|--------|-------------|
| 400 | Bad Request — Invalid input / Duplicate phone |
| 401 | Unauthorized — Invalid/missing token |
| 403 | Forbidden — Insufficient permissions |
| 404 | Not Found — Resource not found |
| 500 | Internal Server Error |
