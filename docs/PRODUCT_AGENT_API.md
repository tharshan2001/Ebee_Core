# Product & Agent API Quick Reference

## Base URL
```
http://localhost:8085
```

---

## Products

### Get Active Products
**GET** `/api/products/active`

**Access:** ADMIN only

**Headers:**
```
Authorization: Bearer <JWT_TOKEN>
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
      "sku": "PROD-A",
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

## Agents

### Get All Agents
**GET** `/api/agents`

**Access:** ADMIN only

**Headers:**
```
Authorization: Bearer <JWT_TOKEN>
```

**Response:**
```json
{
  "success": true,
  "message": "Agents retrieved successfully",
  "data": [
    {
      "id": "478de22c-9519-4503-829e-56b0236b24ce",
      "agentCode": "NEW001",
      "email": "newtest@email.com",
      "firstName": "New",
      "lastName": "Test",
      "fullName": "New Test",
      "phoneNumber": null,
      "commissionRate": 10.0,
      "isActive": true,
      "userStatus": "ACTIVE"
    }
  ]
}
```

---

## Authentication

### Login
**POST** `/api/auth/login`

**Headers:**
```
Content-Type: application/json
```

**Request:**
```json
{
  "email": "admin@ebee.com",
  "password": "admin123"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "userId": "uuid",
    "email": "admin@ebee.com",
    "firstName": "Admin",
    "lastName": "User",
    "role": "ADMIN",
    "mustChangePassword": true,
    "expiresAt": "2026-05-09T22:17:53"
  }
}
```

### Token Usage
Include JWT token in all API requests:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### Token Refresh
**POST** `/api/auth/refresh`

Uses `refresh_token` cookie to get new access token.