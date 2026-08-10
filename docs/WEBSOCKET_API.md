# WebSocket Real-Time Feed API

Ebee broadcasts order and delivery status changes over WebSocket using STOMP (with SockJS fallback). The admin front-end subscribes to two topics to receive instant updates without polling.

---

## Architecture

```
┌─────────────┐     STOMP/SockJS      ┌──────────────────┐     ApplicationEvent     ┌──────────────────┐
│  Front-End  │◀──────────────────────│  Spring Message   │◀───────────────────────│  OrderService /   │
│  (Browser)  │   /topic/orders       │  Broker           │   @TransactionalEvent  │  DeliveryService  │
│             │   /topic/deliveries   │                   │   AFTER_COMMIT          │                   │
└─────────────┘                       └──────────────────┘                         └──────────────────┘
```

**Flow:**
1. Service method (create/update/cancel) modifies the database and publishes a Spring `ApplicationEvent`.
2. If the transaction commits, the `@TransactionalEventListener(AFTER_COMMIT)` fires.
3. The listener builds a lightweight DTO and calls `SimpMessagingTemplate.convertAndSend()`.
4. The message broker pushes the JSON payload to all STOMP subscribers on the matching topic.

---

## Connection

### Endpoint

```
ws://localhost:8085/ws
```

SockJS is enabled — the client can use any of the SockJS transports (WebSocket, XHR, XHR-streaming, iframe). In production use `https://` with the reverse proxy forwarding `/ws/**` to the backend.

### Authentication

Every STOMP `CONNECT` frame must include the JWT in the `Authorization` header:

```
CONNECT
Authorization: Bearer <JWT_TOKEN>
accept-version:1.1,1.0
heart-beat:10000,10000
```

The `WebSocketAuthInterceptor` validates the token, extracts the email and role, and sets the authenticated principal on the STOMP session. If the token is missing, expired, or invalid the connection is rejected.

### CORS

The `/ws/**` path is included in the CORS configuration alongside REST endpoints. `frameOptions` is set to `sameOrigin` to allow SockJS iframe transports.

---

## Topics

| Topic | Roles | Description |
|-------|-------|-------------|
| `/topic/orders` | `ADMIN`, `SUPER_ADMIN` | Order creation and status changes |
| `/topic/deliveries` | `ADMIN`, `SUPER_ADMIN`, `AGENT`, `DRIVER` | Delivery assignment and status changes |
| `/topic/trips` | `ADMIN`, `SUPER_ADMIN`, `DRIVER` | Trip status changes (start, complete, cancel) |
| `/topic/trip-orders/{driverId}` | `ADMIN`, `SUPER_ADMIN`, `DRIVER` | Trip order markers pushed to a driver (DRIVER scoped to their own `driverId`) |
| `/topic/active-trips` | `ADMIN`, `SUPER_ADMIN` | Full snapshot of all IN_PROGRESS trips (dashboard widget) |
| `/topic/driver-locations` | `ADMIN`, `SUPER_ADMIN` | Live driver GPS pings from the mobile app |

Subscribing with a role that is not in the allowed set results in an immediate DISCONNECT with an error message. Destinations not registered in the authorization registry (e.g. `/user/queue/...`) are open to all authenticated users.

**DRIVER scoping:** on `/topic/trip-orders/{driverId}`, a DRIVER may only subscribe to their own driver id — the id in the topic is resolved from the JWT principal (email → user → driver) and a mismatch is rejected with a `MessageDeliveryException`. ADMIN/SUPER_ADMIN may subscribe to any driver id.

---

## Order Feed — `/topic/orders`

### Payload

```json
{
  "orderId": "uuid",
  "orderNumber": "ORD-20260606123456",
  "previousStatus": "PENDING",
  "newStatus": "CONFIRMED",
  "agentName": "Jeeva Frien",
  "customerName": "Ranil Wickramasinghe",
  "customerPhone": "+94771234567",
  "total": 120.00,
  "paidAmount": 120.00,
  "occurredAt": "2026-07-25T10:30:00",
  "created": false
}
```

| Field | Type | Description |
|-------|------|-------------|
| `orderId` | UUID | Order ID |
| `orderNumber` | String | Human-readable order number |
| `previousStatus` | Enum/null | Status before the change; `null` on creation |
| `newStatus` | Enum | Status after the change |
| `agentName` | String/null | Assigned agent's full name |
| `customerName` | String | Customer's full name |
| `customerPhone` | String | Customer phone number |
| `total` | BigDecimal | Order total amount |
| `paidAmount` | BigDecimal | Amount already paid |
| `occurredAt` | LocalDateTime | Timestamp of the status change |
| `created` | boolean | `true` when a new order is created (`previousStatus` is `null`) |

### Status values

`PENDING`, `CONFIRMED`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `RETURNED`

### Events broadcast

| Trigger | `created` | `previousStatus` | `newStatus` |
|---------|-----------|-------------------|-------------|
| New order created | `true` | `null` | `PENDING` |
| Status updated | `false` | previous value | new value |
| Order cancelled | `false` | previous value | `CANCELLED` |
| Order returned | `false` | previous value | `RETURNED` |

---

## Delivery Feed — `/topic/deliveries`

### Payload

```json
{
  "orderId": "uuid",
  "orderNumber": "ORD-20260606123456",
  "previousStatus": "PENDING",
  "newStatus": "ASSIGNED",
  "driverId": "uuid",
  "driverName": "Driver One",
  "customerName": "Ranil Wickramasinghe",
  "district": "Batticaloa",
  "reason": null,
  "occurredAt": "2026-07-25T10:35:00"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `orderId` | UUID | Order ID |
| `orderNumber` | String | Human-readable order number |
| `previousStatus` | Enum | Delivery status before the change |
| `newStatus` | Enum | Delivery status after the change |
| `driverId` | UUID/null | Assigned driver's user ID |
| `driverName` | String/null | Assigned driver's full name |
| `customerName` | String | Customer's full name |
| `district` | String | Delivery district |
| `reason` | String/null | Failure reason (set on `FAILED`/`FAILED_PERMANENT`) |
| `occurredAt` | LocalDateTime | Timestamp of the status change |

### Status values

`PENDING`, `ASSIGNED`, `PICKED_UP`, `IN_TRANSIT`, `DELIVERED`, `FAILED`, `FAILED_PERMANENT`

---

## Trip Feed — `/topic/trips`

Broadcast when a trip changes status (started, completed, or cancelled).

### Payload

```json
{
  "tripId": "uuid",
  "tripName": "Morning Route Colombo",
  "previousStatus": "PLANNED",
  "newStatus": "IN_PROGRESS",
  "driverId": "uuid",
  "driverName": "Driver One",
  "orderCount": 5,
  "occurredAt": "2026-07-30T08:00:00"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `tripId` | UUID | Trip ID |
| `tripName` | String | Trip name |
| `previousStatus` | Enum | Status before the change |
| `newStatus` | Enum | Status after the change |
| `driverId` | UUID/null | Assigned driver's ID |
| `driverName` | String/null | Assigned driver's full name |
| `orderCount` | int | Number of orders on the trip |
| `occurredAt` | LocalDateTime | Timestamp of the status change |

### Status values

`PLANNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`

### Events broadcast

| Trigger | `previousStatus` | `newStatus` |
|---------|------------------|-------------|
| Admin starts trip | `PLANNED` | `IN_PROGRESS` |
| Driver starts trip | `PLANNED` | `IN_PROGRESS` |
| Driver completes trip | `IN_PROGRESS` | `COMPLETED` |
| Admin completes trip | `IN_PROGRESS` | `COMPLETED` |
| Admin cancels trip | `PLANNED` or `IN_PROGRESS` | `CANCELLED` |

---

## Active Trips Feed — `/topic/active-trips`

Broadcast whenever trip-related state commits while trips are running, so an admin dashboard can render a live "active trips" widget. The payload is a **replace-style snapshot** of every IN_PROGRESS trip — subscribers simply overwrite their state with the latest message (no diffing).

### Payload

```json
{
  "activeTrips": [
    {
      "id": "uuid",
      "name": "Morning Route Colombo",
      "status": "IN_PROGRESS",
      "driverName": "Driver One",
      "orderCount": 5,
      "deliveredOrderCount": 2,
      "failedOrderCount": 0,
      "expectedAmount": 25000.00,
      "collectedAmount": 12000.00,
      "netAmount": 11500.00
    }
  ],
  "activeTripsCount": 1
}
```

| Field | Type | Description |
|-------|------|-------------|
| `activeTrips` | `TripResponse[]` | Full list of IN_PROGRESS trips (same shape as `GET /api/trips?status=IN_PROGRESS`) |
| `activeTripsCount` | long | Number of IN_PROGRESS trips |

### When the snapshot is broadcast

| Trigger | Notes |
|---------|-------|
| Trip started (`PLANNED` → `IN_PROGRESS`) | Trip joins the snapshot |
| Trip completed or cancelled (`IN_PROGRESS` → `COMPLETED`/`CANCELLED`) | Trip leaves the snapshot |
| Orders added to / removed from a trip | Totals and counts change |
| Delivery status change on an IN_PROGRESS trip | `deliveredOrderCount`/`failedOrderCount` update. Delivery events for orders **not** on an IN_PROGRESS trip are filtered out before the rebuild |

### Rebuilding

The snapshot is rebuilt by re-querying all trips with status `IN_PROGRESS` after the originating transaction commits, so the broadcast always reflects durable state. An empty `activeTrips` array means no trips are currently running.

---

## Trip Orders Feed — `/topic/trip-orders/{driverId}`

Broadcast when orders are added to or removed from a trip assigned to that driver, so the driver app can refresh its delivery list/route in real time. Subscribing DRIVERs are scoped to their own `driverId` (mismatched subscriptions are rejected).

### Payload

```json
{
  "tripId": "uuid",
  "driverId": "uuid",
  "orderMarkers": [
    {
      "orderId": "uuid",
      "orderNumber": "ORD-000123",
      "customerName": "Kumar Perera",
      "customerPhone": "0771234567",
      "street": "123 Galle Rd",
      "addressLine2": null,
      "city": "Colombo 03",
      "postalCode": "00300",
      "district": "Colombo",
      "country": "LK",
      "deliveryStatus": "PENDING",
      "orderStatus": "SHIPPED",
      "totalAmount": 15000.00,
      "formattedAddress": "123 Galle Rd, Colombo 03, 00300",
      "status": "PENDING"
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `tripId` | UUID | Trip ID |
| `driverId` | UUID | Assigned driver's ID |
| `orderMarkers` | List<TripOrderMarkerDto> | Current order markers for the trip (see `GET /api/trips/{id}/order-markers`) |

Each marker contains the order id/number, customer contact, address fields, `formattedAddress`, `deliveryStatus`, `orderStatus`, `totalAmount`, and a `status` alias mirroring `deliveryStatus`. Sent whenever a trip's orders are changed or the trip is started.

---

## Driver Locations — `/topic/driver-locations`

Live GPS pings from the bee-driver mobile app. Each location update is also stored in Redis (`driver:location:{driverId}`) with a 5-minute TTL so late subscribers can hydrate the map on load.

### Payload

```json
{
  "driverId": "uuid",
  "driverName": "Driver One",
  "latitude": 6.9271,
  "longitude": 79.8612,
  "accuracy": 10.0,
  "bearing": 90.0,
  "speed": 15.5,
  "recordedAt": "2026-07-25T10:30:00Z",
  "photoUrl": "https://storage.ebee.lk/.../avatar.jpg or null"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `driverId` | UUID | Driver's user ID |
| `driverName` | String | Driver's full name |
| `latitude` | number | GPS latitude |
| `longitude` | number | GPS longitude |
| `accuracy` | number/null | Horizontal accuracy in meters |
| `bearing` | number/null | Heading in degrees (0–360) |
| `speed` | number/null | Speed in m/s |
| `recordedAt` | Instant | Server timestamp when the location was received |
| `photoUrl` | String/null | Driver's profile picture URL (enriched from DB if missing) |

### Source

The bee-driver mobile app posts to `POST /api/driver/location` every ~15 seconds while location tracking is active. Each post is broadcast to `/topic/driver-locations` and cached in Redis.

### REST companion endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/admin/driver-locations/active` | ADMIN, SUPER_ADMIN | All currently cached driver locations (from Redis) |
| `GET` | `/api/admin/driver-locations/{driverId}` | ADMIN, SUPER_ADMIN | Latest known location for one driver |
| `POST` | `/api/driver/location` | DRIVER | Submit a new location ping (throttled to 15s on the client) |
| `GET` | `/api/driver/location` | DRIVER | Get the driver's own last known location |

---

## Server-Side Components

### Configuration

| Class | Role |
|-------|------|
| `WebSocketConfig` | Registers `/ws` SockJS endpoint, enables `/topic` simple broker, wires the auth interceptor |
| `WebSocketAuthInterceptor` | Validates JWT on CONNECT, enforces role-based access on SUBSCRIBE |
| `TopicAuthorization` | Record pairing a topic prefix with the set of allowed roles |
| `TopicAuthorizationRegistry` | Central registry of topic→role mappings; new topics added here, not in the interceptor |

### Order broadcast

| Class | Role |
|-------|------|
| `OrderStatusChangedEvent` | Spring `ApplicationEvent` carrying the order, previous status, and `created` flag |
| `OrderStatusChangedEventListener` | `@TransactionalEventListener(AFTER_COMMIT)` — builds `OrderStatusChangedMessage` and sends to `/topic/orders` |
| `OrderStatusChangedMessage` | Immutable record DTO serialized to JSON |
| `OrderServiceImpl` | Publishes `OrderStatusChangedEvent` in `createOrder()`, `updateOrderStatus()`, `cancelOrder()`, `returnOrder()` |

### Delivery broadcast

| Class | Role |
|-------|------|
| `DeliveryStatusChangedEvent` | Spring `ApplicationEvent` carrying extracted delivery fields (no entity reference) |
| `DeliveryStatusChangedEventListener` | `@TransactionalEventListener(AFTER_COMMIT)` — builds `DeliveryStatusChangedMessage` and sends to `/topic/deliveries` |
| `DeliveryStatusChangedMessage` | Immutable record DTO serialized to JSON |

### Driver location broadcast

| Class | Role |
|-------|------|
| `DriverLocationService` | Caches location in Redis (5-min TTL) and broadcasts to `/topic/driver-locations` |
| `DriverLocationRequest` | Inbound DTO from the mobile app (`latitude`, `longitude`, `accuracy`, `bearing`, `speed`) |
| `DriverLocationResponse` | Outbound DTO broadcast to subscribers (enriched with `photoUrl` from DB) |
| `DriverAppController` | `POST /api/driver/location` — receives pings from the mobile app |
| `DriverLocationAdminController` | `GET /api/admin/driver-locations/active` and `/{driverId}` — reads cached locations from Redis |

---

## Front-End Integration

### STOMP client (`src/lib/stompClient.ts`)

A singleton SockJS+STOMP client shared across all hooks. Connects on first `subscribe()` call, auto-deactivates when the last subscriber unsubscribes. On reconnect, automatically re-subscribes to all active topics.

### Hooks

| Hook | Topic | Purpose |
|------|-------|---------|
| `useOrderFeed(onUpdate)` | `/topic/orders` | Calls `onUpdate` for every order message |
| `useDeliveryFeed(onUpdate)` | `/topic/deliveries` | Calls `onUpdate` for every delivery message |
| `useDriverTracking()` | `/topic/driver-locations` | Hydrates from Redis on mount, then live-updates via STOMP. Returns `{ locations, connected }` |
| `useStompFeed<T>(topic, parse, onUpdate)` | any | Generic hook used by `useOrderFeed` and `useDeliveryFeed` |

### Optimistic UI pattern

All mutations follow this pattern:

1. Update local state immediately (optimistic).
2. Fire the REST API call.
3. On success — optionally reconcile with server response.
4. On failure — revert to previous state and show an error toast.

The real-time feed handles the cross-client case: when another admin creates or updates an order, the local list is updated or prepended without a manual refetch.

### Delivery Kanban auto-refresh

The delivery board subscribes to both `/topic/deliveries` and `/topic/orders`. When an order status changes to `SHIPPED`, the delivery list is automatically refetched so the newly created `PENDING` delivery card appears without a page refresh.

---

## Security Notes

- JWT is validated once on STOMP CONNECT. Subsequent frames use the session principal — no re-validation.
- The interceptor parses the role from the `ROLE_*` granted authority set during CONNECT.
- `TopicAuthorizationRegistry` is the single source of truth for topic access. To add a new broadcast topic, register it there — the interceptor reads the registry dynamically.
- Failed broadcasts (e.g. no active subscribers) are logged but do not propagate — a broken WebSocket cannot roll back a committed transaction.

---

## CORS

`/ws/**` is included in the `SecurityConfig.corsConfigurationSource()` alongside REST endpoints. The SockJS iframe transport requires `frameOptions.sameOrigin()` (not `deny`) to function correctly.

---

## Typical Client Flow

```
1. Client opens WebSocket → CONNECT with JWT
2. Client subscribes to /topic/orders and /topic/deliveries
3. Admin creates order → all connected admins see the new order instantly
4. Admin marks order SHIPPED → delivery Kanban auto-refreshes, new PENDING card appears
5. Driver updates delivery → admin sees status change in real time
```
