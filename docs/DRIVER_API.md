# Driver Module API

Drivers extend the User entity with delivery-specific attributes (license, contact, availability).

All `{id}` path parameters are UUIDs in the standard 36-character format (e.g., `f47ac10b-58cc-4372-a567-0e02b2c3d479`). Plain numeric values will be rejected.

---

## Availability Lifecycle

```
AVAILABLE ──assign delivery──▶ ON_TRIP ──deliver──▶ AVAILABLE
     ↘  OFFLINE   (manual)
     ↘  SUSPENDED (manual)
```

Available values: `AVAILABLE`, `ON_TRIP`, `OFFLINE`, `SUSPENDED`.

---

## Auto-Management

- **Delivery assigned** → driver availability auto-set to `ON_TRIP`
- **Delivery DELIVERED** → driver availability auto-returned to `AVAILABLE`
- **Delivery FAILED → PENDING (retry)** → driver availability auto-restored to `AVAILABLE` (if still assigned at failure time)
- **Delivery assignment rejected** → if driver is already `ON_TRIP` or `SUSPENDED`, the assignment throws `400` and does not change status

This ensures a driver cannot be double-booked — they are `ON_TRIP` from the moment a delivery is assigned until it is marked `DELIVERED` (or the delivery is reset to `PENDING` for retry).

---

## Endpoints

### Create Driver

Creates a User (with role `DRIVER`) and a Driver profile in one request. No pre-existing user is needed.

```
POST /api/drivers
Auth: ADMIN
Content-Type: application/json
Body:
{
    "email": "driver@example.com",
    "firstName": "Driver",
    "lastName": "One",
    "phoneNumber": "+94771234567",
    "licenseNumber": "B1234567",
    "contactNumber": "+94771234567"
}
```

All fields except `phoneNumber`, `licenseNumber`, and `contactNumber` are required.

**Success Response (201):**
```json
{
    "success": true,
    "message": "Driver created successfully",
    "data": {
        "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
        "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "email": "driver@example.com",
        "firstName": "Driver",
        "lastName": "One",
        "licenseNumber": "B1234567",
        "contactNumber": "+94771234567",
        "availability": "AVAILABLE",
        "isActive": true,
        "createdAt": "2026-06-06T10:30:00",
        "generatedPassword": "aB3$xY9!pQ2@"
    }
}
```

The `generatedPassword` is only returned on creation. The driver must change it on first login.

### List Drivers

```
GET /api/drivers?availability=AVAILABLE&page=0&size=20&sort=createdAt,desc
Auth: ADMIN, DRIVER
```

Optional filter: `availability` (`AVAILABLE`, `ON_TRIP`, `OFFLINE`, `SUSPENDED`).

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `availability` | String | — | Filter by availability status |
| `page` | Integer | `0` | Zero-based page index |
| `size` | Integer | `20` | Page size (max 100) |
| `sort` | String | `createdAt,desc` | Format: `field,direction` |

**Response (200):** `PageResponse<DriverResponse>` wrapper (see `PageResponse` shape in README).

```bash
curl -s "http://localhost:8085/api/drivers?page=0&size=20&sort=createdAt,desc" \
  -H "Authorization: Bearer <TOKEN>"
```

### List Available Drivers

```
GET /api/drivers/available?page=0&size=50
Auth: ADMIN, DRIVER
```

Paginated shorthand for `GET /api/drivers?availability=AVAILABLE`. Max page size is 100.

### Get Driver Detail

```
GET /api/drivers/{id}
Auth: ADMIN, DRIVER
```

`{id}` — the driver's UUID (returned at creation).

### Update Driver

```
PATCH /api/drivers/{id}
Auth: ADMIN
Body:
{
    "email": "driver@example.com",
    "firstName": "Driver",
    "lastName": "One",
    "phoneNumber": "+94771234567",
    "licenseNumber": "B7654321",
    "contactNumber": "+94771234678"
}
```

Note: `email`, `firstName`, `lastName` update the underlying User record.

### Update Availability

```
PATCH /api/drivers/{id}/availability
Auth: ADMIN, DRIVER
Body:
{
    "availability": "ON_TRIP"
}
```

Valid values: `AVAILABLE`, `ON_TRIP`, `OFFLINE`, `SUSPENDED`.

> **Authorization:** Drivers can only update **their own** availability. A non-admin driver attempting to change another driver's availability receives `403 Forbidden`. Admins can update any driver.


