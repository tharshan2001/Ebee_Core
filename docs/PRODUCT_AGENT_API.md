# Product & Agent API Reference

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

### Supported Districts
Agents are assigned to one of the following districts (North & East Sri Lanka only). The district determines the auto-generated agent code prefix.

| Code | District |
|------|----------|
| JF   | Jaffna   |
| KN   | Kilinochchi |
| MN   | Mannar   |
| ML   | Mullaitivu |
| VN   | Vavuniya |
| TM   | Trincomalee |
| BC   | Batticaloa |
| AP   | Ampara   |

Agent code format: `{PREFIX}-{SEQ}` (e.g., `BC-003`, `JF-001`). Sequences are per-district — each district starts at 001.

---

### Create Agent
**POST** `/api/agents`

**Access:** ADMIN only (`multipart/form-data`)

**Headers:**
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: multipart/form-data
```

**Form Fields:**

| Field                  | Type     | Required | Description                                         |
|------------------------|----------|----------|-----------------------------------------------------|
| email                  | string   | yes      | Agent's email (unique)                              |
| firstName              | string   | yes      | First name                                          |
| lastName               | string   | yes      | Last name                                           |
| commissionRate         | number   | yes      | Commission rate (percentage, e.g. 5.0)              |
| district               | string   | yes      | District name (see table above)                     |
| address                | string   | no       | Agent's address                                     |
| phoneNumber            | string   | no       | Phone number                                        |
| nicNumber              | string   | no       | NIC number                                          |
| nicDocument            | file     | no       | NIC document (PDF/image)                            |
| bankPassbookDocument   | file     | no       | Bank passbook document (PDF/image)                  |
| bankAccountNumber      | string   | no       | Bank account number                                 |
| bankName               | string   | no       | Bank name                                           |
| bankBranch             | string   | no       | Bank branch                                         |

**cURL Example:**
```bash
curl -X POST http://localhost:8085/api/agents \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -F "email=agent@example.com" \
  -F "firstName=John" \
  -F "lastName=Doe" \
  -F "commissionRate=5.0" \
  -F "address=123 Main Street, Jaffna" \
  -F "district=Jaffna" \
  -F "phoneNumber=0771234567"
```

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Agent created successfully",
  "data": {
    "agentId": "uuid",
    "userId": "uuid",
    "email": "agent@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "agentCode": "JF-001",
    "address": "123 Main Street, Jaffna",
    "district": "Jaffna",
    "phoneNumber": "0771234567",
    "commissionRate": 5.0,
    "nicNumber": null,
    "bankAccountNumber": null,
    "bankName": null,
    "bankBranch": null,
    "userStatus": "ACTIVE",
    "isActive": true,
    "mustChangePassword": false,
    "createdAt": "2026-06-02T23:17:32.567441",
    "generatedPassword": "5nFy$Z9U$YWB"
  }
}
```

> `agentCode` is auto-generated based on the district. `generatedPassword` is only returned in this create response.
>
> **On creation, the system automatically sends a welcome notification** to the agent via email and SMS containing their login credentials (agent code, email, and password). Email/SMS failures are logged but do not block agent creation.

---

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
      "id": "uuid",
      "userId": "uuid",
      "email": "agent@example.com",
      "firstName": "John",
      "lastName": "Doe",
      "fullName": "John Doe",
      "agentCode": "JF-001",
      "address": "123 Main Street, Jaffna",
      "district": "Jaffna",
      "phoneNumber": "0771234567",
      "commissionRate": 5.0,
      "totalSales": 0.0,
      "totalCommission": 0.0,
      "isActive": true,
      "userStatus": "ACTIVE",
      "nicNumber": null,
      "nicDocumentUrl": null,
      "nicDocumentName": null,
      "nicDocumentType": null,
      "bankPassbookDocumentUrl": null,
      "bankPassbookDocumentName": null,
      "bankPassbookDocumentType": null,
      "bankAccountNumber": null,
      "bankName": null,
      "bankBranch": null,
      "createdAt": "2026-06-02T23:17:32.567441",
      "updatedAt": "2026-06-02T23:17:32.567441"
    }
  ]
}
```

---

### Get Agent by ID
**GET** `/api/agents/{id}`

**Access:** ADMIN only

**Headers:**
```
Authorization: Bearer <JWT_TOKEN>
```

**Response:** Same as single item in Get All Agents above.

---

### Update Agent
**PATCH** `/api/agents/{id}`

**Access:** ADMIN only (`multipart/form-data`)

**Headers:**
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: multipart/form-data
```

**Form Fields:**

| Field                  | Type     | Required | Description                        |
|------------------------|----------|----------|------------------------------------|
| agentCode              | string   | yes      | Agent code (manually settable)     |
| commissionRate         | number   | yes      | Commission rate                    |
| district               | string   | no       | Change district                    |
| address                | string   | no       | Change address                     |
| phoneNumber            | string   | no       | Change phone number                |
| nicNumber              | string   | no       | NIC number                         |
| nicDocument            | file     | no       | NIC document (replaces existing)   |
| bankPassbookDocument   | file     | no       | Bank passbook (replaces existing)  |
| bankAccountNumber      | string   | no       | Bank account number                |
| bankName               | string   | no       | Bank name                          |
| bankBranch             | string   | no       | Bank branch                        |

**cURL Example:**
```bash
curl -X PATCH http://localhost:8085/api/agents/{id} \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -F "agentCode=JF-001" \
  -F "commissionRate=7.0" \
  -F "address=456 New Road" \
  -F "district=Kilinochchi" \
  -F "phoneNumber=0777654321"
```

**Response:** Same as single item in Get All Agents.

---

### Update Commission Rate Only
**PATCH** `/api/agents/{id}/commission-rate`

**Access:** ADMIN only

**Headers:**
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/x-www-form-urlencoded
```

**Parameters:**
```
commissionRate=10.0
```

**cURL Example:**
```bash
curl -X PATCH "http://localhost:8085/api/agents/{id}/commission-rate?commissionRate=10.0" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response:**
```json
{
  "success": true,
  "message": "Commission rate updated successfully",
  "data": {
    "id": "uuid",
    "agentCode": "JF-001",
    "commissionRate": 10.0,
    ...
  }
}
```

---

### Delete Agent
**DELETE** `/api/agents/{id}`

**Access:** ADMIN only

**Headers:**
```
Authorization: Bearer <JWT_TOKEN>
```

**Response:**
```json
{
  "success": true,
  "message": "Agent deleted successfully"
}
```

---

## Welcome Notification (On Agent Creation)

When an admin creates an agent, the system automatically sends a welcome notification via both email and SMS:

**Email:** Sent to the agent's email address with subject _"Welcome to Ebee — Agent Registration Successful"_
**SMS:** Sent to the agent's phone number

**Message content:**
```
Dear {FirstName} {LastName},

Congratulations! Your agent account has been successfully created.

Your Login Credentials:
───────────────────────────
Agent Code : JF-001
Email      : agent@example.com
Password   : 5nFy$Z9U$YWB
───────────────────────────

Please log in and change your password at your earliest convenience.

Best regards,
Ebee Team
```

> Notification failures (e.g., invalid email, SMS API down) are logged as warnings and do **not** block agent creation or throw errors.

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
    "expiresAt": "2026-06-02T22:17:53"
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
