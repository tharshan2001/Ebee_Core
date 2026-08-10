# Driver App API

The Driver App API provides mobile-friendly endpoints for drivers to manage deliveries, profile, expenses, trips, and receive real-time updates.

All endpoints are under `/api/driver/*` and require authentication with `ROLE_DRIVER`.

---

## Trip-Based Delivery Assignment

Deliveries are assigned to drivers **via trips**, not directly:

1. Admin creates a trip and assigns a driver
2. Admin adds orders to the trip
3. Driver starts the trip → only then sees those orders in their delivery list
4. Driver completes the trip → orders no longer appear in their delivery list

**Key rule:** A driver can only have ONE active (IN_PROGRESS) trip at a time.

---

## State Transitions (Driver-Facing)

Drivers can transition deliveries through this flow:

```
ASSIGNED ──▶ PICKED_UP ──▶ IN_TRANSIT ──▶ DELIVERED
     ↘ FAILED ──▶ PICKED_UP (retry)
```

**Driver cannot:**
- Assign or reassign deliveries (admin-only via trip management)
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

**Important:** This endpoint returns ONLY orders from the driver's **active (IN_PROGRESS) trip**. If the driver has no active trip, returns an empty list.

Optional filter: `status` (`ASSIGNED`, `PICKED_UP`, `IN_TRANSIT`, `DELIVERED`, `FAILED`).

Sortable fields: `createdAt`, `updatedAt`, `orderNumber`, `deliveryStatus`, `scheduledDate`, `deliveredAt`.

```bash
curl -s "http://localhost:8085/api/driver/deliveries?status=ASSIGNED&page=0&size=20" \
  -H "Authorization: Bearer <TOKEN>"
```

**Response:** `PageResponse<DriverDeliveryResponse>`

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

## Trip Management

### List My Trips

```
GET /api/driver/trips
Auth: DRIVER
```

Returns all trips assigned to the current driver.

```json
{
    "success": true,
    "data": [
        {
            "id": "uuid",
            "name": "Morning Route Colombo",
            "startDate": "2026-07-30",
            "endDate": "2026-07-30",
            "status": "IN_PROGRESS",
            "notes": "Morning deliveries",
            "mileageKm": 45.5,
            "orderCount": 5,
            "paidOrderCount": 3,
            "partialOrderCount": 1,
            "unpaidOrderCount": 1,
            "expectedAmount": 15000.00,
            "collectedAmount": 10000.00,
            "outstandingAmount": 5000.00,
            "netAmount": 8500.00,
            "totalExpenses": 1500.00
        }
    ]
}
```

---

### Get Trip by ID

```
GET /api/driver/trips/{tripId}
Auth: DRIVER
```

Returns trip details. Driver can only view their own trips.

---

### Start Trip

```
PATCH /api/driver/trips/{tripId}/start
Auth: DRIVER
```

Transitions trip from PLANNED to IN_PROGRESS.

**Rules:**
- Trip must be in PLANNED status
- Driver must be assigned to the trip
- Driver cannot have another active (IN_PROGRESS) trip

Starting the trip assigns all PENDING orders on the trip (delivery → `ASSIGNED`) and marks the driver ON_TRIP.

**Response:** `TripResponse`

---

### Add Orders to Trip

```
PATCH /api/driver/trips/{tripId}/orders
Auth: DRIVER
Content-Type: application/json
```

```json
{
    "orderIdsToAdd": ["uuid1", "uuid2"],
    "orderIdsToRemove": ["uuid3"]
}
```

**Note:** Typically admin adds orders, but driver can also add/remove orders. Adds require the order to be `SHIPPED` and not already on another trip (orders on another trip are rejected, not silently reassigned). Removed unresolved orders are fully detached (delivery reset to PENDING); DELIVERED/FAILED_PERMANENT orders only lose the trip reference.

**Response:** `TripResponse`

---

### Update Mileage

```
PATCH /api/driver/trips/{tripId}/mileage?mileageKm=45.5
Auth: DRIVER
```

Records the total distance driven for the trip.

---

### Complete Trip

```
PATCH /api/driver/trips/{tripId}/complete
Auth: DRIVER
```

Transitions trip from IN_PROGRESS to COMPLETED.

**Rules:**
- Trip must be in IN_PROGRESS status
- **Every order must be terminal:** delivery status `DELIVERED` or `FAILED_PERMANENT`, or order status `CANCELLED`/`RETURNED`/`RETURN_EXCHANGE`
- If any order is still `PENDING`/`ASSIGNED`/`PICKED_UP`/`IN_TRANSIT`/`FAILED`, completion is rejected with an error listing those order numbers — orders are not detached or reset

**Response:** `TripResponse`

---

### Create Trip Expense

```
POST /api/driver/trips/{tripId}/expenses
Auth: DRIVER
Content-Type: multipart/form-data
```

| Part | Type | Required | Description |
|------|------|----------|-------------|
| `expense` | JSON string | Yes | Expense details |
| `receipt` | File | No | Receipt image |

`expense` JSON structure:
```json
{
    "category": "PETROL",
    "amount": 5000.00,
    "description": "Fuel for route",
    "dateIncurred": "2026-07-30"
}
```

Valid categories: `PETROL`, `MILEAGE`, `PARKING`, `TOLL`, `TRAVEL`, `MAINTENANCE`, `OTHER`.

- For PETROL: `amount > 0`, `distanceKm` should be null
- For MILEAGE: `amount = 0`, `distanceKm > 0` (distance in km)

Expense is linked to both the trip and the driver.

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
    "category": "PETROL",
    "amount": 5000.00,
    "description": "Fuel for deliveries in Batticaloa",
    "dateIncurred": "2026-06-29"
}
```

Valid categories: `PETROL`, `MILEAGE`, `MAINTENANCE`, `PARKING`, `TOLL`, `TRAVEL`, `OTHER`.

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
- [Trip Management API](TRIP_API.md) — Admin trip management endpoints
- [Driver Assignment Rules](DRIVER_ASSIGNMENT.md) — Business rules for availability derivation
- [Auth API](AUTH_API.md) — Login, token refresh, and password management
- [Alert API](ALERT_API.md) — In-app alert management
- [Profile API](PROFILE_API.md) — User profile management (photo upload, settings)
