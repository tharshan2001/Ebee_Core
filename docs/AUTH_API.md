# Authentication API

> **Rate Limiting:** Auth endpoints are rate-limited. Login is capped at 5 attempts per 15 minutes per email. Generic endpoints (forgot-password, verify-otp, reset-password, refresh) are capped at 10 requests per 15 minutes. All endpoints are capped at 100 requests per minute per IP. Excess requests return `429 Too Many Requests`.

---

## Register

**POST** `/api/auth/register`

**Access:** ADMIN only (`@PreAuthorize("hasRole('ADMIN')")`)

**Request:**
```json
{
  "email": "newagent@ebee.com",
  "password": "SecureP@ss1",
  "firstName": "New",
  "lastName": "Agent",
  "phoneNumber": "+94771234567",
  "role": "AGENT"
}
```

> **Password requirements:** Minimum 8 characters, must contain at least one uppercase letter, one lowercase letter, one digit, and one special character (`@#$%^&+=!`).

**Response:**
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": { ... }
}
```

---

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

Optionally validate the user's role matches by including `role`:
```json
{
  "email": "admin@ebee.com",
  "password": "admin123",
  "role": "ADMIN"
}
```

**Response (admin login):**
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
    "expiresAt": "2026-06-02T22:17:53",
    "driverId": null,
    "licenseNumber": null,
    "contactNumber": null,
    "driverAvailability": null
  }
}
```

**Response (driver login):**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "userId": "uuid",
    "email": "driver@ebee.com",
    "firstName": "Driver",
    "lastName": "User",
    "role": "DRIVER",
    "mustChangePassword": true,
    "expiresAt": "2026-06-02T22:17:53",
    "driverId": "uuid",
    "licenseNumber": "LIC-12345",
    "contactNumber": "0771234567",
    "driverAvailability": "AVAILABLE"
  }
}
```

The `driverId`, `licenseNumber`, `contactNumber`, and `driverAvailability` fields are `null` for non-DRIVER users.

**Error (role mismatch):** `403` with no token issued
```json
{
  "success": false,
  "message": "Access denied. You do not have permission to perform this action.",
  "data": null
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

**Response:** Same as login response — includes `mustChangePassword`, and driver fields (`driverId`, `licenseNumber`, `contactNumber`, `driverAvailability`) when the user is a DRIVER:

```json
{
  "success": true,
  "message": "Token refreshed successfully",
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

For a DRIVER user, driver fields are populated:
```json
{
  "success": true,
  "message": "Token refreshed successfully",
  "data": {
    "token": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "userId": "uuid",
    "email": "driver@ebee.com",
    "firstName": "Driver",
    "lastName": "User",
    "role": "DRIVER",
    "mustChangePassword": true,
    "expiresAt": "2026-06-02T22:17:53",
    "driverId": "uuid",
    "licenseNumber": "LIC-12345",
    "contactNumber": "0771234567",
    "driverAvailability": "AVAILABLE"
  }
}
```

---

## Change Password

**POST** `/api/auth/change-password`

**Access:** Authenticated users

**Request:**
```json
{
  "currentPassword": "admin123",
  "newPassword": "NewSecurePass@456"
}
```

> **Password requirements:** Minimum 8 characters, must contain at least one uppercase letter, one lowercase letter, one digit, and one special character (`@#$%^&+=!`).

**Response:**
```json
{
  "success": true,
  "message": "Password changed successfully",
  "data": null
}
```

---

## Forgot Password (Request OTP)

**POST** `/api/auth/forgot-password`

**Access:** Public (no auth required)

Sends a 6-digit OTP to the user's email and phone number (if available). OTP expires in 5 minutes.

**Request:**
```json
{
  "email": "admin@ebee.com"
}
```

**Response:**
```json
{
  "success": true,
  "message": "OTP sent to your email and phone",
  "data": null
}
```

> **Note:** The response is identical for existing and non-existing emails. This endpoint does not reveal whether an email is registered (prevents email enumeration).

---

## Verify OTP

**POST** `/api/auth/verify-otp`

**Access:** Public (no auth required)

Verifies the OTP and returns a short-lived **reset token** (JWT, 5 min expiry) for the reset-password step.

**Request:**
```json
{
  "email": "admin@ebee.com",
  "otp": "483291"
}
```

**Response:**
```json
{
  "success": true,
  "message": "OTP verified successfully",
  "data": {
    "resetToken": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

**Error (invalid/expired OTP):**
```json
{
  "success": false,
  "message": "Invalid or expired OTP",
  "data": null
}
```

---

## Reset Password

**POST** `/api/auth/reset-password`

**Access:** Public (no auth required)

Uses the reset token from verify-otp to set a new password. The reset token is a stateless JWT that expires in 5 minutes.

**Request:**
```json
{
  "resetToken": "eyJhbGciOiJIUzI1NiJ9...",
  "newPassword": "MyNewSecureP@ss1"
}
```

> **Password requirements:** Minimum 8 characters, must contain at least one uppercase letter, one lowercase letter, one digit, and one special character (`@#$%^&+=!`).

**Response:**
```json
{
  "success": true,
  "message": "Password reset successfully",
  "data": null
}
```

**Error (invalid/expired reset token):**
```json
{
  "success": false,
  "message": "Invalid or expired reset token",
  "data": null
}
```

---

## Complete Reset Flow (end-to-end)

1. `POST /api/auth/forgot-password` with `email` → OTP sent via email + SMS
2. `POST /api/auth/verify-otp` with `email` + `otp` → returns `resetToken`
3. `POST /api/auth/reset-password` with `resetToken` + `newPassword` → password updated
