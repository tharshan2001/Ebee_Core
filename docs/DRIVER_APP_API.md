# Driver App API

The Driver App API provides mobile-friendly endpoints for drivers to manage deliveries, profile, expenses, and receive real-time updates.

All endpoints are under `/api/driver/*` and require authentication with `ROLE_DRIVER`.

---

## State Transitions (Driver-Facing)

Drivers can transition deliveries through this flow:

```
ASSIGNED ──▶ PICKED_UP ──▶ IN_TRANSIT ──▶ DELIVERED
     ↘ FAILED ──▶ PICKED_UP (retry)
```

**Driver cannot:**
- Assign or reassign deliveries (admin-only via `PATCH /api/deliveries/{orderId}/assign`)
- Mark as `FAILED_PERMANENT` (admin-only)
- Change availability to `SUSPENDED`

---

## Dashboard

```
GET /api/driver/dashboard
Auth: DRIVER
```

Returns a summary of the driver's current state:

```json
{
    "success": true,
    "message": "Dashboard retrieved successfully",
    "data": {
        "availability": "AVAILABLE",
        "activeDeliveriesCount": 2,
        "completedTodayCount": 3,
        "failedTodayCount": 0,
        "unreadAlertsCount": 1,
        "recentDeliveries": [
            {
                "orderId": "uuid",
                "orderNumber": "ORD-20260629123456",
                "customerName": "Ranil Wickramasinghe",
                "customerPhone": "0771234567",
                "customerStreet": "123 Main St",
                "customerCity": "Batticaloa Town",
                "customerDistrict": "Batticaloa",
                "deliveryStatus": "IN_TRANSIT",
                "totalAmount": 15000.00,
                "items": [
                    {
                        "productName": "Product A",
                        "quantity": 2,
                        "unitPrice": 5000.00,
                        "subtotal": 10000.00
                    }
                ]
            }
        ]
    }
}
```

**Dashboard response fields:**

| Field | Type | Description |
|-------|------|-------------|
| `availability` | String | Current driver availability (`AVAILABLE`, `ON_TRIP`, `OFFLINE`) |
| `activeDeliveriesCount` | Long | Orders with status ASSIGNED, PICKED_UP, or IN_TRANSIT |
| `completedTodayCount` | Long | Deliveries marked DELIVERED today |
| `failedTodayCount` | Long | Deliveries marked FAILED today |
| `unreadAlertsCount` | Long | Unread in-app alerts |
| `recentDeliveries` | Array | Last 5 deliveries assigned to this driver |

---

## List My Deliveries

```
GET /api/driver/deliveries?status=ASSIGNED&page=0&size=20&sort=updatedAt,desc
Auth: DRIVER
```

Optional filter: `status` (`ASSIGNED`, `PICKED_UP`, `IN_TRANSIT`, `DELIVERED`, `FAILED`).

Sortable fields: `createdAt`, `updatedAt`, `orderNumber`, `deliveryStatus`, `scheduledDate`, `deliveredAt`.

```bash
curl -s "http://localhost:8085/api/driver/deliveries?status=ASSIGNED&page=0&size=20" \
  -H "Authorization: Bearer <TOKEN>"
```

**Response:** `PageResponse<DriverDeliveryResponse>`.

---

## Get Delivery Detail

```
GET /api/driver/deliveries/{orderId}
Auth: DRIVER
```

Returns full delivery details including items and customer address:

```json
{
    "success": true,
    "data": {
        "orderId": "uuid",
        "orderNumber": "ORD-20260629123456",
        "customerName": "Ranil Wickramasinghe",
        "customerPhone": "0771234567",
        "customerStreet": "123 Main St",
        "customerCity": "Batticaloa Town",
        "customerDistrict": "Batticaloa",
        "customerPostalCode": "30000",
        "deliveryStatus": "ASSIGNED",
        "orderStatus": "CONFIRMED",
        "driverId": "uuid",
        "driverName": "Driver One",
        "scheduledDate": "2026-06-30",
        "notes": "Handle with care",
        "totalAmount": 25000.00,
        "paidAmount": 10000.00,
        "outstandingBalance": 15000.00,
        "paymentStatus": "PARTIALLY_PAID",
        "deliveryPhotoUrl": null,
        "deliverySignatureUrl": null,
        "items": [
            {
                "productId": "uuid",
                "productName": "Product A",
                "productItemCode": "PRD-001",
                "quantity": 2,
                "unitPrice": 5000.00,
                "subtotal": 10000.00
            },
            {
                "productId": "uuid",
                "productName": "Product B",
                "productItemCode": "PRD-002",
                "quantity": 1,
                "unitPrice": 15000.00,
                "subtotal": 15000.00
            }
        ]
    }
}
```

---

## Update Delivery Status

```
PATCH /api/driver/deliveries/{orderId}/status
Auth: DRIVER
Content-Type: application/json
Body:
{
    "status": "PICKED_UP",
    "reason": null,
    "driverNotes": "Package received in good condition"
}
```

**Allowed transitions for drivers:**

| Current Status | Next Status | Description |
|---------------|-------------|-------------|
| `ASSIGNED` | `PICKED_UP` | Driver picked up the package |
| `ASSIGNED` | `FAILED` | Driver reports failure |
| `PICKED_UP` | `IN_TRANSIT` | En route to customer |
| `PICKED_UP` | `FAILED` | Driver reports failure |
| `IN_TRANSIT` | `DELIVERED` | Successfully delivered |
| `IN_TRANSIT` | `FAILED` | Driver reports failure |
| `FAILED` | `PICKED_UP` | Retry after failure |

The `reason` field is required when setting status to `FAILED` (stored as `deliveryFailureReason`). The `driverNotes` field is optional at any stage.

---

## Complete Delivery (with Photo/Signature)

```
POST /api/driver/deliveries/{orderId}/complete
Auth: DRIVER
Content-Type: multipart/form-data
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `photo` | File | No | Delivery proof photo (jpeg, png, webp) |
| `signature` | File | No | Customer signature image (jpeg, png) |
| `driverNotes` | String | No | Driver notes about the delivery |

Automatically transitions delivery status to `DELIVERED`, sets `deliveredAt`, and uploads photo/signature to S3. Driver availability is reset to `AVAILABLE`.

Works from any active status (`ASSIGNED`, `PICKED_UP`, `IN_TRANSIT`).

---

## Update Availability

```
PATCH /api/driver/availability
Auth: DRIVER
Content-Type: application/json
Body:
{
    "availability": "OFFLINE"
}
```

Valid values: `AVAILABLE`, `OFFLINE`.

**Cannot set:** `SUSPENDED` (admin-only), `ON_TRIP` (auto-derived from active deliveries).

Setting to `AVAILABLE` fails if the driver has active deliveries (`ASSIGNED`, `PICKED_UP`, `IN_TRANSIT`).

---

## My Profile

### Get Profile
```
GET /api/driver/profile
Auth: DRIVER
```

```json
{
    "success": true,
    "data": {
        "driverId": "uuid",
        "userId": "uuid",
        "email": "driver@ebee.com",
        "firstName": "Driver",
        "lastName": "One",
        "phoneNumber": "0771234567",
        "licenseNumber": "B1234567",
        "availability": "AVAILABLE",
        "isActive": true,
        "profilePictureUrl": "https://s3.amazonaws.com/...",
        "createdAt": "2026-06-01T10:00:00",
        "updatedAt": "2026-06-29T12:00:00"
    }
}
```

### Update Profile
```
PATCH /api/driver/profile
Auth: DRIVER
Content-Type: application/json
Body:
{
    "firstName": "Driver",
    "lastName": "Updated",
    "phoneNumber": "0771234567",
    "licenseNumber": "B7654321"
}
```

All fields optional. Only provided fields are updated.

---

## Performance Stats

```
GET /api/driver/stats
Auth: DRIVER
```

```json
{
    "success": true,
    "data": {
        "totalDeliveriesCompleted": 42,
        "totalDeliveriesFailed": 3,
        "deliveriesThisWeek": 5,
        "deliveriesThisMonth": 18,
        "averageDeliveryTimeHours": 4.50,
        "totalExpensesClaimed": 15000.00,
        "totalExpensesCount": 5
    }
}
```

---

## Expenses

### Create Expense
```
POST /api/driver/expenses
Auth: DRIVER
Content-Type: multipart/form-data
```

| Part | Type | Required | Description |
|------|------|----------|-------------|
| `expense` | JSON string | Yes | Expense details (see below) |
| `receipt` | File | No | Receipt image/PDF |

`expense` JSON structure:
```json
{
    "category": "FUEL",
    "amount": 5000.00,
    "description": "Fuel for deliveries in Batticaloa",
    "dateIncurred": "2026-06-29"
}
```

Valid categories: `FUEL`, `MAINTENANCE`, `PARKING`, `TOLL`, `OTHER`.

The driver is automatically set as the `driverId` on the expense.

### List My Expenses
```
GET /api/driver/expenses?status=APPROVED
Auth: DRIVER
```

Optional filter: `status` (`PENDING`, `APPROVED`, `REJECTED`).

---

## Device Token Registration (Push Notifications)

```
POST /api/driver/device-token
Auth: DRIVER
Content-Type: application/json
Body:
{
    "fcmToken": "fcm-token-string-from-device"
}
```

Registers the device's Firebase Cloud Messaging token for push notifications. Stores the token on the Driver record for future notification delivery.

---

## Common Response Shape

All endpoints return the standard `ApiResponse` wrapper:

```json
{
    "success": true,
    "message": "Action completed successfully",
    "data": { ... },
    "timestamp": "2026-06-29T10:30:00"
}
```

Pagination uses `PageResponse`:

```json
{
    "content": [ ... ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 42,
    "totalPages": 3,
    "first": true,
    "last": false
}
```

Error responses:
```json
{
    "success": false,
    "message": "This delivery is not assigned to you",
    "data": null,
    "timestamp": "2026-06-29T10:30:00"
}
```

---

## Related Documentation

- [Driver Module API](DRIVER_API.md) — Admin endpoints for driver CRUD and availability
- [Delivery Module API](DELIVERY_API.md) — Delivery state machine, assignment, and admin endpoints
- [Driver Assignment Rules](DRIVER_ASSIGNMENT.md) — Business rules for availability derivation
- [Auth API](AUTH_API.md) — Login, token refresh, and password management
- [Alert API](ALERT_API.md) — In-app alert management
- [Profile API](PROFILE_API.md) — User profile management (photo upload, settings)
