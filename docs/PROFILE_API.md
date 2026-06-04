# Profile API

**Base URL:** `http://localhost:8085/api/profile`

---

## Authentication

All profile endpoints require JWT authentication:
```
Authorization: Bearer <JWT_TOKEN>
```

All authenticated users (ADMIN, AGENT, DRIVER) can access their own profile.

---

## Get Profile

**GET** `/api/profile`

**Access:** Authenticated users

Returns the current user's profile information.

**Response:**
```json
{
  "success": true,
  "message": "Profile retrieved successfully",
  "data": {
    "id": "5b718629-785e-4adf-a475-01e7c5957670",
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

**Example curl:**
```bash
curl -s http://localhost:8085/api/profile \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

## Update Profile

**PUT** `/api/profile`

**Access:** Authenticated users

Updates the current user's name and phone number.

**Request:**
```json
{
  "firstName": "Admin",
  "lastName": "User",
  "phoneNumber": "+94771234567"
}
```

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `firstName` | Yes | String | User's first name |
| `lastName` | Yes | String | User's last name |
| `phoneNumber` | No | String | Phone number for SMS notifications / OTP delivery |

**Response:**
```json
{
  "success": true,
  "message": "Profile updated successfully",
  "data": { "...same shape as GET..." }
}
```

**Example curl:**
```bash
curl -s -X PUT http://localhost:8085/api/profile \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Admin","lastName":"User","phoneNumber":"+94771234567"}'
```

---

## Upload Profile Photo

**PUT** `/api/profile/photo`

**Access:** Authenticated users

Uploads a profile picture. The file is stored in S3 under the `profiles/` folder. Replaces any existing photo.

**Request:** `multipart/form-data`

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `file` | Yes | File | Image file (max 10MB per server config) |

**Response:**
```json
{
  "success": true,
  "message": "Photo uploaded successfully",
  "data": {
    "profilePictureUrl": "https://s3.eu-north-1.amazonaws.com/pathfinder-app-media/profiles/uuid-filename.jpg",
    "profilePictureName": "avatar.jpg",
    "profilePictureType": "image/jpeg"
  }
}
```

**Example curl:**
```bash
curl -s -X PUT http://localhost:8085/api/profile/photo \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -F "file=@/path/to/photo.jpg"
```

**Error (empty file):**
```json
{
  "success": false,
  "message": "File is empty",
  "data": null
}
```

---

## Delete Profile Photo

**DELETE** `/api/profile/photo`

**Access:** Authenticated users

Removes the profile picture from S3 and clears the photo fields on the user record.

**Response:**
```json
{
  "success": true,
  "message": "Photo deleted successfully",
  "data": null
}
```

**Example curl:**
```bash
curl -s -X DELETE http://localhost:8085/api/profile/photo \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

## Update Settings

**PUT** `/api/profile/settings`

**Access:** Authenticated users

Updates user preferences such as login alerts.

**Request:**
```json
{
  "loginAlertsEnabled": false
}
```

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `loginAlertsEnabled` | No | Boolean | Enable/disable email + SMS alerts on new login (default: `true`) |

**Response:**
```json
{
  "success": true,
  "message": "Settings updated successfully",
  "data": { "...same shape as GET..." }
}
```

**Example curl:**
```bash
curl -s -X PUT http://localhost:8085/api/profile/settings \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"loginAlertsEnabled": false}'
```

---

## Error Responses

| HTTP Status | Scenario | Example |
|-------------|----------|---------|
| 401 / 403 | Missing or invalid JWT token | Empty body (Spring Security) |
| 400 | Validation failure or empty file | `"File is empty"` |
| 500 | Server error or S3 upload failure | `"An unexpected error occurred..."` |
