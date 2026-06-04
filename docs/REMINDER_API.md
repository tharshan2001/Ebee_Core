# Reminder / Calendar API

**Base URL:** `http://localhost:8085/api/reminders`

---

## Authentication

All endpoint require JWT with ADMIN role:
```
Authorization: Bearer <JWT_TOKEN>
```

---

## Date Range (Month/Week View)

**GET** `/api/reminders`

Returns reminders for a date range — maps to **Tailwind month/week** calendar views.

**Query Parameters:**

| Param | Type | Format | Default | Description |
|-------|------|--------|---------|-------------|
| `startDate` | LocalDate | `yyyy-MM-dd` | 1st of current month | Start date (inclusive) |
| `endDate` | LocalDate | `yyyy-MM-dd` | Last day of start month | End date (inclusive) |

**Example curl:**
```bash
# Month view
curl -s "http://localhost:8085/api/reminders?startDate=2026-06-01&endDate=2026-06-30" \
  -H "Authorization: Bearer <JWT_TOKEN>"

# Day view
curl -s "http://localhost:8085/api/reminders?startDate=2026-06-05&endDate=2026-06-05" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response:**
```json
{
  "success": true,
  "message": "Reminders retrieved successfully",
  "data": [
    {
      "id": "7017518a-...",
      "userId": "5b718629-...",
      "title": "Team Standup",
      "description": "Daily standup meeting",
      "reminderDate": "2026-06-05",
      "reminderTime": "09:00:00",
      "status": "PENDING",
      "priority": "HIGH",
      "category": "meeting",
      "sendEmail": true,
      "sendSms": true,
      "notifiedAt": null,
      "completedAt": null,
      "createdAt": "2026-06-04T20:59:00",
      "updatedAt": "2026-06-04T20:59:00"
    }
  ]
}
```

---

## Today (Sidebar Widget)

**GET** `/api/reminders/today`

Returns all reminders for today — maps to **Tailwind small calendar** sidebar widget.

**Response:** Same shape as list endpoint.

**Example curl:**
```bash
curl -s http://localhost:8085/api/reminders/today \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

## Get Single Reminder

**GET** `/api/reminders/{id}`

**Example curl:**
```bash
curl -s http://localhost:8085/api/reminders/7017518a-4b9a-4727-99e1-c1ab715f9020 \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

## Create Reminder

**POST** `/api/reminders`

**Request:**
```json
{
  "title": "Team Standup",
  "description": "Daily standup meeting",
  "reminderDate": "2026-06-05",
  "reminderTime": "09:00:00",
  "priority": "HIGH",
  "category": "meeting",
  "sendEmail": true,
  "sendSms": true
}
```

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `title` | Yes | String | Reminder title |
| `description` | No | String | Optional details |
| `reminderDate` | Yes | LocalDate | `yyyy-MM-dd` |
| `reminderTime` | No | LocalTime | `HH:mm:ss` (all-day if null) |
| `priority` | No | Enum | `LOW`, `MEDIUM`, `HIGH` (default: `MEDIUM`) |
| `category` | No | String | e.g. "meeting", "task", "follow-up" |
| `sendEmail` | No | Boolean | Send email notification (default: `false`) |
| `sendSms` | No | Boolean | Send SMS notification (default: `false`) |

**Response:** HTTP 201 with the created reminder.

**Example curl:**
```bash
curl -s -X POST http://localhost:8085/api/reminders \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Team Standup",
    "reminderDate": "2026-06-05",
    "reminderTime": "09:00:00",
    "priority": "HIGH",
    "category": "meeting",
    "sendEmail": true,
    "sendSms": true
  }'
```

---

## Update Reminder

**PUT** `/api/reminders/{id}`

Same body as create. All fields required except `description`, `priority`, `category`, `sendEmail`, `sendSms` (null = unchanged).

**Example curl:**
```bash
curl -s -X PUT http://localhost:8085/api/reminders/7017518a-... \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Team Standup (Updated)",
    "reminderDate": "2026-06-05",
    "reminderTime": "09:30:00",
    "priority": "HIGH",
    "category": "meeting",
    "sendEmail": true,
    "sendSms": false
  }'
```

---

## Complete Reminder

**PATCH** `/api/reminders/{id}/complete`

Marks a reminder as `COMPLETED` and sets `completedAt`.

**Example curl:**
```bash
curl -s -X PATCH http://localhost:8085/api/reminders/7017518a-.../complete \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

## Delete Reminder

**DELETE** `/api/reminders/{id}`

**Example curl:**
```bash
curl -s -X DELETE http://localhost:8085/api/reminders/7017518a-... \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

## Scheduled Notifications

The `ReminderSchedulerService` runs every **60 seconds** and:

1. **Early alerts (5 min before):** Sends email + SMS with "Coming up in 5 minutes" for reminders with a set time and `sendEmail`/`sendSms` enabled.
2. **Exact alerts (at the time):** Sends email + SMS with "Due now" when the clock reaches `reminderTime` on `reminderDate`.

All-day reminders (no `reminderTime`) fire the exact alert at midnight of the reminder date.

---

## Frontend Integration (Tailwind UI)

| Tailwind Calendar View | API Call |
|------------------------|----------|
| **Month view** | `GET /api/reminders?startDate=2026-06-01&endDate=2026-06-30` |
| **Week view** | `GET /api/reminders?startDate=2026-06-01&endDate=2026-06-07` |
| **Day view** | `GET /api/reminders?startDate=2026-06-05&endDate=2026-06-05` |
| **Small calendar (sidebar)** | `GET /api/reminders/today` |

---

## Error Responses

| HTTP Status | Scenario |
|-------------|----------|
| 403 | Missing/invalid JWT or non-ADMIN role |
| 404 | Reminder not found |
| 500 | Internal server error |
