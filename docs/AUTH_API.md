# Authentication API

## Login

**POST** `/api/auth/login`

**Access:** Public (no auth required)

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

---

## Token Usage

Include the JWT token in all authenticated requests:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

## Refresh Token

**POST** `/api/auth/refresh`

**Access:** Public (uses `refresh_token` cookie)

Uses the `refresh_token` HTTP-only cookie (set on login) to issue a new access token.
