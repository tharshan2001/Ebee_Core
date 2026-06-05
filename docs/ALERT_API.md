# Alert API (In-App Notifications)

**Base URL:** `http://localhost:8085/api/alerts`

Alerts are internal in-app notifications stored in the database and visible to the recipient on login. They are created **alongside** email/SMS notifications (not as a replacement).

**Recipients:** Alerts target specific users (admin, agent). Unlike email/SMS, alerts are not sent to customers.

---

## Authentication

All endpoints require a valid JWT token (any authenticated role — ADMIN, AGENT):

```
Authorization: Bearer <JWT_TOKEN>
```

Alerts are **automatically scoped** to the authenticated user — you only see your own alerts.

---

## List Alerts (Notification Feed)

**GET** `/api/alerts`

Returns a paginated list of alerts for the authenticated user, ordered by `createdAt` descending (newest first).

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | `0` | Zero-based page number |
| `size` | int | `20` | Page size (max results per page) |

**Example curl:**
```bash
curl -s "http://localhost:8085/api/alerts?page=0&size=20" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response:**
```json
{
  "success": true,
  "message": "Alerts retrieved successfully",
  "data": {
    "content": [
      {
        "id": "c58af9f7-c55d-4a0b-807a-9f1c8a00869e",
        "title": "Commission Approved – Order ORD-001",
        "message": "Dear John,\n\nYour commission for order ORD-001 has been approved.",
        "alertType": "COMMISSION_APPROVED",
        "referenceId": "a1b2c3d4-...",
        "referenceType": "commission",
        "isRead": false,
        "createdAt": "2026-06-05T01:11:54.948367"
      }
    ],
    "pageable": { "pageNumber": 0, "pageSize": 20, ... },
    "totalElements": 1,
    "totalPages": 1,
    "numberOfElements": 1,
    "first": true,
    "last": true,
    "empty": false
  }
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Alert identifier |
| `title` | String | Alert title / subject |
| `message` | String | Alert body text |
| `alertType` | Enum | See [Alert Types](#alert-types) below |
| `referenceId` | UUID | ID of the related entity (order, commission, etc.) |
| `referenceType` | String | Related entity type: `order`, `commission`, `agent`, `reminder`, `agent_application` |
| `isRead` | Boolean | Whether the user has acknowledged this alert |
| `createdAt` | Timestamp | When the alert was created |

---

## Get Unread Count (Badge Number)

**GET** `/api/alerts/unread-count`

Returns the number of unread alerts — used to show a badge on the notification bell in the UI.

**Example curl:**
```bash
curl -s http://localhost:8085/api/alerts/unread-count \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response:**
```json
{
  "success": true,
  "message": "Unread count retrieved",
  "data": {
    "count": 3
  }
}
```

---

## Mark Single Alert as Read

**PATCH** `/api/alerts/{id}/read`

Marks a single alert as read by its ID. Only the alert's recipient can mark it as read (returns 403 otherwise).

**Example curl:**
```bash
curl -s -X PATCH http://localhost:8085/api/alerts/c58af9f7-c55d-4a0b-807a-9f1c8a00869e/read \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response:**
```json
{
  "success": true,
  "message": "Alert marked as read",
  "data": null
}
```

---

## Mark All as Read

**PATCH** `/api/alerts/read-all`

Marks every unread alert for the authenticated user as read.

**Example curl:**
```bash
curl -s -X PATCH http://localhost:8085/api/alerts/read-all \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response:**
```json
{
  "success": true,
  "message": "All alerts marked as read",
  "data": null
}
```

---

## Alert Types

| Enum Value | Trigger | Target | Description |
|------------|---------|--------|-------------|
| `AGENT_APPROVED` | Agent approved / welcome | AGENT | Application approved or account created |
| `AGENT_REJECTED` | Agent rejected | AGENT | Application rejected |
| `AGENT_ACTIVATED` | Agent activated | AGENT | Account reactivated |
| `AGENT_DEACTIVATED` | Agent deactivated | AGENT | Account deactivated |
| `ORDER_CREATED` | Order created | AGENT | New order assigned |
| `ORDER_STATUS_CHANGED` | Order status updated | AGENT | CONFIRMED / PROCESSING / SHIPPED / DELIVERED / RETURNED |
| `ORDER_CANCELLED` | Order cancelled | AGENT | Order cancelled |
| `COMMISSION_CALCULATED` | Commission calculated | AGENT | Commission calculated for an order |
| `COMMISSION_APPROVED` | Commission approved | AGENT | Commission approved by admin |
| `COMMISSION_PROCESSING` | Commission processing | AGENT | Commission being processed for payment |
| `COMMISSION_PAID` | Commission paid | AGENT | Commission paid out |
| `COMMISSION_REJECTED` | Commission rejected | AGENT | Commission rejected |
| `COMMISSION_CANCELLED` | Commission cancelled | AGENT | Commission cancelled (order cancelled) |
| `REMINDER_DUE` | Reminder scheduler | Any user | Reminder exact alert or 5-min early alert |

---

## Alert Lifecycle

Alerts are created automatically by the following business operations:

1. **Agent submitted application** → all ADMIN users get an alert (via `AgentApplicationService.create()`)
2. **Agent account status changes** (approved/rejected/activated/deactivated) → the AGENT gets an alert (via `AgentService.sendAgentStatusNotification()`)
3. **New agent created** → the AGENT gets a welcome alert (via `AgentService.sendWelcomeNotification()`)
4. **Order assigned / status changed / cancelled** → the assigned AGENT gets an alert (via `OrderService`)
5. **Commission calculated / approved / processing / paid / rejected / cancelled** → the AGENT gets an alert (via `CommissionLifecycleService`, `CommissionCalculationService`)
6. **Reminder scheduler fires** (exact + 5-min early) → the reminder owner gets an alert (via `ReminderSchedulerService`)

---

## Frontend Integration (Notification Bell)

| UI Element | API Call |
|------------|----------|
| **Badge count** (bell icon) | `GET /api/alerts/unread-count` → poll every 30-60s |
| **Dropdown preview** | `GET /api/alerts?page=0&size=5` → show 5 most recent |
| **Full notification page** | `GET /api/alerts?page=0&size=20` → paginated list |
| **Mark as read (individual)** | `PATCH /api/alerts/{id}/read` → on click |
| **Mark all as read** | `PATCH /api/alerts/read-all` → on "Mark all read" button |

---

## Error Responses

| HTTP Status | Scenario |
|-------------|----------|
| 401 | Missing or invalid JWT |
| 403 | Attempting to mark another user's alert as read |
| 404 | Alert not found |
| 500 | Internal server error |
