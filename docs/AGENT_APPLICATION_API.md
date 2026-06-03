# Agent Application API Reference

Public-facing endpoint for prospective agents to submit applications. Admin can list, view, and delete submissions.

**Base URL:** `http://localhost:8085/api/agent-applications`

---

## Submit Application (Public)

**POST** `/api/agent-applications`

**Access:** Public (no auth required)

**Headers:** `Content-Type: application/json`

**Request:**
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

## Admin Endpoints

All admin endpoints require the Authorization header:
```
Authorization: Bearer <JWT_TOKEN>
```

### Get All Applications

**GET** `/api/agent-applications`

**Response:** Array of application objects (same format as single item above).

### Get Application by ID

**GET** `/api/agent-applications/{id}`

**Response:** Single application object.

### Delete Application

**DELETE** `/api/agent-applications/{id}`

**Response:**
```json
{
  "success": true,
  "message": "Application deleted successfully"
}
```

---

## Workflow

1. Prospective agent submits application via public endpoint.
2. Admin reviews applications via GET All.
3. Admin creates an agent manually via `POST /api/agents` using the applicant's details, adding `address` and `district` at that time.
4. Admin optionally deletes the application entry.
