# User Management API

**Base URL:** `http://localhost:8085/api/users`

## Authentication

All endpoints require JWT:
```
Authorization: Bearer <JWT_TOKEN>
```

**Role:** Most endpoints require `ADMIN`. `GET /me` and `GET /{id}` are accessible to any authenticated user. `PATCH /{id}` allows ADMIN or the user themselves.

---

## Create User

**POST** `/api/users`

**Access:** ADMIN

Creates a new user with the specified role.

**Request Body:**
```json
{
  "email": "newuser@ebee.com",
  "password": "securePass123",
  "firstName": "New",
  "lastName": "User",
  "phoneNumber": "+94771234567",
  "role": "AGENT",
  "status": "ACTIVE"
}
```

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `email` | Yes | String | Valid email address |
| `password` | No | String | Password (auto-generated if omitted) |
| `firstName` | Yes | String | User's first name |
| `lastName` | Yes | String | User's last name |
| `phoneNumber` | No | String | Phone number |
| `role` | Yes | `RoleType` | `ADMIN`, `AGENT`, or `DRIVER` |
| `status` | No | `UserStatus` | `ACTIVE`, `INACTIVE`, or `LOCKED` (default: `ACTIVE`) |

**Response (201):**
```json
{
  "success": true,
  "message": "User created successfully",
  "data": {
    "id": "7a5a36c9-8423-464c-8697-9b30f0025ce0",
    "email": "newuser@ebee.com",
    "firstName": "New",
    "lastName": "User",
    "phoneNumber": "+94771234567",
    "role": "AGENT",
    "status": "ACTIVE",
    "isActive": true,
    "mustChangePassword": true,
    "createdAt": "2026-06-10T10:00:00",
    "updatedAt": "2026-06-10T10:00:00"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | User's unique ID |
| `email` | `String` | Email address |
| `firstName` | `String` | First name |
| `lastName` | `String` | Last name |
| `phoneNumber` | `String` | Phone number |
| `role` | `RoleType` | `ADMIN`, `AGENT`, or `DRIVER` |
| `status` | `UserStatus` | `ACTIVE`, `INACTIVE`, or `LOCKED` |
| `isActive` | `Boolean` | Whether user is active |
| `mustChangePassword` | `Boolean` | Whether password change is required on next login |
| `createdAt` | `LocalDateTime` | Record creation timestamp |
| `updatedAt` | `LocalDateTime` | Last update timestamp |

---

## Get Current User

**GET** `/api/users/me`

**Access:** Authenticated users

Returns the profile of the currently authenticated user. Overlaps with `GET /api/profile`.

**Response:**
```json
{
  "success": true,
  "message": "User profile retrieved successfully",
  "data": {
    "id": "7a5a36c9-8423-464c-8697-9b30f0025ce0",
    "email": "admin@ebee.com",
    "firstName": "Admin",
    "lastName": "User",
    "phoneNumber": "+94771234567",
    "role": "ADMIN",
    "profilePictureUrl": null,
    "loginAlertsEnabled": true,
    "phoneVerified": false,
    "createdAt": "2026-05-15T10:30:00"
  }
}
```

---

## Get User by ID

**GET** `/api/users/{id}`

**Access:** Authenticated users

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | User ID |

**Response:**
```json
{
  "success": true,
  "message": "User retrieved successfully",
  "data": { "...same shape as create response..." }
}
```

---

## List All Users

**GET** `/api/users`

**Access:** ADMIN

Returns all users in the system.

**Response:**
```json
{
  "success": true,
  "message": "Users retrieved successfully",
  "data": [ { "...same shape as create response..." } ]
}
```

---

## Get Users by Role

**GET** `/api/users/role/{role}`

**Access:** ADMIN

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `role` | `String` | Role name: `ADMIN`, `AGENT`, or `DRIVER` |

**Response:**
```json
{
  "success": true,
  "message": "Users retrieved successfully",
  "data": [ { "...same shape as create response..." } ]
}
```

---

## Update User

**PATCH** `/api/users/{id}`

**Access:** ADMIN or the user themselves

Updates user details. The `role` cannot be changed via this endpoint.

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | User ID |

**Request Body:**
```json
{
  "email": "updated@ebee.com",
  "password": "newPassword123",
  "firstName": "Updated",
  "lastName": "Name",
  "phoneNumber": "+94771234568",
  "status": "ACTIVE"
}
```

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `email` | Yes | String | Valid email address |
| `password` | No | String | New password (leave null to keep current) |
| `firstName` | Yes | String | User's first name |
| `lastName` | Yes | String | User's last name |
| `phoneNumber` | No | String | Phone number |
| `status` | No | `UserStatus` | `ACTIVE`, `INACTIVE`, or `LOCKED` |

**Response (200):** Updated user record.

---

## Delete User

**DELETE** `/api/users/{id}`

**Access:** ADMIN

Soft-deletes or removes a user.

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | User ID |

**Response (200):**
```json
{
  "success": true,
  "message": "User deleted successfully",
  "data": null
}
```

---

## Activate User

**PUT** `/api/users/{id}/activate`

**Access:** ADMIN

Re-activates a user that was deactivated or locked.

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | User ID |

**Response (200):** Updated user record with `status: "ACTIVE"` and `isActive: true`.

---

## Error Responses

| HTTP Status | Scenario |
|-------------|----------|
| 401 / 403 | Missing or invalid JWT, or insufficient role |
| 404 | User not found |
| 400 | Validation failure (missing required fields, invalid email format) |
| 409 | Email already in use |
| 500 | Server error |
