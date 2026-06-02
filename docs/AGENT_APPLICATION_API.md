# Agent Application API Reference

Public-facing endpoint for prospective agents to submit applications. Admin can list, view, and delete submissions.

## Base URL
```
http://localhost:8085
```

---

## Submit Application (Public)
**POST** `/api/agent-applications`

**Access:** Public (no auth required)

**Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phoneNumber": "0771234567"
}
```

All fields are required.

**cURL Example:**
```bash
curl -X POST http://localhost:8085/api/agent-applications \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phoneNumber": "0771234567"
  }'
```

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Application submitted successfully",
  "data": {
    "id": "uuid",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phoneNumber": "0771234567",
    "createdAt": "2026-06-02T23:30:00",
    "updatedAt": "2026-06-02T23:30:00"
  }
}
```

> A confirmation email is sent to the applicant on successful submission. Email failures are logged but do not block the response.

---

## Get All Applications (Admin)
**GET** `/api/agent-applications`

**Access:** ADMIN only

**Headers:**
```
Authorization: Bearer <JWT_TOKEN>
```

**Response:**
```json
{
  "success": true,
  "message": "Applications retrieved successfully",
  "data": [
    {
      "id": "uuid",
      "firstName": "John",
      "lastName": "Doe",
      "email": "john.doe@example.com",
      "phoneNumber": "0771234567",
      "createdAt": "2026-06-02T23:30:00",
      "updatedAt": "2026-06-02T23:30:00"
    }
  ]
}
```

---

## Get Application by ID (Admin)
**GET** `/api/agent-applications/{id}`

**Access:** ADMIN only

**Headers:**
```
Authorization: Bearer <JWT_TOKEN>
```

**Response:** Same as single item in Get All response.

---

## Delete Application (Admin)
**DELETE** `/api/agent-applications/{id}`

**Access:** ADMIN only

**Headers:**
```
Authorization: Bearer <JWT_TOKEN>
```

**Response:**
```json
{
  "success": true,
  "message": "Application deleted successfully"
}
```

---

## Workflow
1. Prospective agent submits application via public endpoint
2. Admin reviews applications using Get All
3. Admin creates an agent manually via `POST /api/agents` using the applicant's details, adding `address` and `district` at that time
4. Admin optionally deletes the application entry
