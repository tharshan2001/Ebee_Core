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

Subscribing with a role that is not in the allowed set results in an immediate DISCONNECT with an error message. Destinations not registered in the authorization registry (e.g. `/user/queue/...`) are open to all authenticated users.

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

---

## Front-End Integration

### STOMP client (`src/lib/stompClient.ts`)

A singleton SockJS+STOMP client shared across all hooks. Connects on first `subscribe()` call, auto-deactivates when the last subscriber unsubscribes. On reconnect, automatically re-subscribes to all active topics.

### Hooks

| Hook | Topic | Purpose |
|------|-------|---------|
| `useOrderFeed(onUpdate)` | `/topic/orders` | Calls `onUpdate` for every order message |
| `useDeliveryFeed(onUpdate)` | `/topic/deliveries` | Calls `onUpdate` for every delivery message |
| `useStompFeed<T>(topic, parse, onUpdate)` | any | Generic hook used by the above two |

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
