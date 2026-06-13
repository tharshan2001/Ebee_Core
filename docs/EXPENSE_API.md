# Expense API Documentation

## Base URL
```
http://localhost:8085/api/expenses
```

## Authentication
All expense endpoints require a JWT Bearer token:
```
Authorization: Bearer <JWT_TOKEN>
```

### Roles
- **ADMIN** — Full access (create, read, approve, reject)
- **AGENT** — Create expenses (auto-approved)
- **DRIVER** — Create expenses (auto-approved)

---

## Endpoints

### 1. Create Expense
**POST** `/api/expenses`

Creates a new expense with an optional receipt upload.

**Access:** ADMIN, AGENT, DRIVER

**Request (multipart/form-data):**
```
expense: {"category":"FUEL","amount":5000.00,"description":"Fuel for delivery","dateIncurred":"2026-06-13"}  (application/json part)
receipt: <file_upload>  (optional)
```

**Optional fields in expense JSON:**
- `driverId` (UUID) — associate expense with a driver

**Auto-approval:** Expenses created by ADMIN or AGENT are automatically set to `APPROVED`. DRIVER-created expenses start as `PENDING`.

**Response (201):**
```json
{
  "success": true,
  "message": "Expense created successfully",
  "data": {
    "id": "d7f16457-8708-4ead-ac7d-f82bb295f24f",
    "category": "FUEL",
    "amount": 5000.00,
    "description": "Fuel for delivery",
    "receiptUrl": null,
    "dateIncurred": "2026-06-13",
    "status": "APPROVED",
    "createdById": "5b718629-...",
    "createdByEmail": "admin@ebee.com",
    "createdByName": "Admin User",
    "driverId": null,
    "driverName": null,
    "approvedById": "5b718629-...",
    "approvedByName": "Admin User",
    "approvedAt": "2026-06-13T22:08:21.960464",
    "rejectionReason": null,
    "paidAt": null,
    "paymentReference": null,
    "createdAt": null,
    "updatedAt": null
  }
}
```

---

### 2. List All Expenses
**GET** `/api/expenses`

Retrieves a paginated list of all expenses.

**Access:** ADMIN

**Query Parameters:**
| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | 0 | Zero-based page index |
| `size` | int | 20 | Page size (max 200) |

**Response (200):**
```json
{
  "success": true,
  "message": "Expenses retrieved successfully",
  "data": {
    "content": [ ... ],
    "totalElements": 4,
    "totalPages": 1,
    ...
  }
}
```

---

### 3. Get My Expenses
**GET** `/api/expenses/my`

Retrieves expenses created by the authenticated user.

**Access:** Authenticated

**Response (200):**
```json
{
  "success": true,
  "message": "My expenses retrieved successfully",
  "data": [ ... ]
}
```

---

### 4. Get Expense by ID
**GET** `/api/expenses/{id}`

Retrieves a single expense by its UUID.

**Access:** ADMIN or the expense owner

**Response (200):** Single expense object

---

### 5. Approve Expense
**PUT** `/api/expenses/{id}/approve`

Approves a PENDING expense.

**Access:** ADMIN

**Response (200):**
```json
{
  "success": true,
  "message": "Expense approved successfully",
  "data": { "status": "APPROVED", ... }
}
```

**Error (400):** If expense is not in PENDING status.

---

### 6. Reject Expense
**PUT** `/api/expenses/{id}/reject`

Rejects a PENDING expense with an optional reason.

**Access:** ADMIN

**Query Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| `reason` | string | Optional rejection reason |

**Response (200):**
```json
{
  "success": true,
  "message": "Expense rejected successfully",
  "data": { "status": "REJECTED", "rejectionReason": "...", ... }
}
```

**Error (400):** If expense is not in PENDING status.

---

## Expense Categories

| Category | Description |
|----------|-------------|
| TRAVEL | Travel-related expenses |
| FUEL | Fuel and petrol expenses |
| OFFICE_SUPPLIES | Office supply purchases |
| UTILITIES | Utility bill payments |
| MAINTENANCE | Vehicle or equipment maintenance |
| OTHER | Miscellaneous expenses |

## Expense Statuses

| Status | Description |
|--------|-------------|
| PENDING | Awaiting admin approval |
| APPROVED | Approved by admin |
| REJECTED | Rejected by admin |
