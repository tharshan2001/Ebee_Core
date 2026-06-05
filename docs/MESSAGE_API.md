# Message API (Admin Broadcasting)

**Base URL:** `http://localhost:8085/api/messages`

The Message API allows **ADMIN** users to broadcast promotional or informational messages to agents and customers. Messages are delivered via **SMS** and **email** asynchronously.

Messages are one-way — recipients cannot reply. Each message creates an audit record with recipient details and delivery status flags.

---

## Authentication

All endpoints require JWT with ADMIN role:

```
Authorization: Bearer <JWT_TOKEN>
```

---

## Target Types

| Target Type | Description | `recipientIds` | `manualRecipients` |
|---|---|---|---|
| `ALL_AGENTS` | Every agent in the system | Not required | Not required |
| `ALL_CUSTOMERS` | Every customer in the system | Not required | Not required |
| `SELECTED_AGENTS` | Specific agents by ID | Required | Not required |
| `SELECTED_CUSTOMERS` | Specific customers by ID | Required | Not required |
| `MANUAL_ENTRY` | Manually entered phone numbers | Not required | Required |

---

## Send a Message

**POST** `/api/messages`

**Request:**

```json
{
  "subject": "Summer Sale!",
  "body": "20% off all items until Sunday.",
  "targetType": "ALL_AGENTS"
}
```

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `subject` | Yes | String | Message subject line |
| `body` | Yes | String | Message body text |
| `targetType` | Yes | Enum | `ALL_AGENTS`, `ALL_CUSTOMERS`, `SELECTED_AGENTS`, `SELECTED_CUSTOMERS`, `MANUAL_ENTRY` |
| `recipientIds` | Conditional | UUID[] | Required when targetType is `SELECTED_*` |
| `manualRecipients` | Conditional | Object[] | Required when targetType is `MANUAL_ENTRY`. Each entry: `{ "phoneNumber": "required", "name": "optional", "email": "optional" }` |

**Example curl — broadcast to all agents:**

```bash
curl -s -X POST http://localhost:8085/api/messages \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "Summer Sale!",
    "body": "20% off all items until Sunday.",
    "targetType": "ALL_AGENTS"
  }'
```

**Example curl — selected agents:**

```bash
curl -s -X POST http://localhost:8085/api/messages \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "Personal Bonus",
    "body": "You have been selected for a special bonus.",
    "targetType": "SELECTED_AGENTS",
    "recipientIds": ["<AGENT_UUID>"]
  }'
```

**Example curl — manual entry (phone numbers):**

```bash
curl -s -X POST http://localhost:8085/api/messages \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "Flash Sale",
    "body": "50% off today only!",
    "targetType": "MANUAL_ENTRY",
    "manualRecipients": [
      {"phoneNumber": "+94771234567", "name": "John Doe", "email": "john@example.com"},
      {"phoneNumber": "+94777654321", "name": "Jane Smith"}
    ]
  }'
```

**Response (HTTP 201):**

```json
{
  "success": true,
  "message": "Message sent successfully",
  "data": {
    "id": "9a6d341f-737b-4cec-9d86-ba3cb1965e15",
    "subject": "Summer Sale!",
    "targetType": "ALL_AGENTS",
    "totalRecipients": 54,
    "sentAt": "2026-06-05T21:53:41.236701"
  }
}
```

---

## Sent Messages History

**GET** `/api/messages/sent`

Returns a paginated list of messages sent by the authenticated admin, ordered by `sentAt` descending.

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | `0` | Zero-based page number |
| `size` | int | `20` | Page size |

**Example curl:**

```bash
curl -s "http://localhost:8085/api/messages/sent?page=0&size=20" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response:**

```json
{
  "success": true,
  "message": "Sent messages retrieved",
  "data": {
    "content": [
      {
        "id": "eaab47ab-...",
        "subject": "Team reward",
        "targetType": "SELECTED_AGENTS",
        "totalRecipients": 2,
        "sentAt": "2026-06-05T22:28:48"
      }
    ],
    "pageable": { "pageNumber": 0, "pageSize": 20, ... },
    "totalElements": 9,
    "totalPages": 1,
    "numberOfElements": 9,
    "first": true,
    "last": true,
    "empty": false
  }
}
```

---

## Message Detail with Recipients

**GET** `/api/messages/{id}`

Returns a single message with its recipient list and delivery status flags. The detail view is paginated by recipient.

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | `0` | Recipient list page |
| `size` | int | `20` | Recipients per page |

**Example curl:**

```bash
curl -s "http://localhost:8085/api/messages/eaab47ab-9316-42a0-9425-bd47b0c07498?page=0&size=20" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response:**

```json
{
  "success": true,
  "message": "Message retrieved",
  "data": {
    "id": "eaab47ab-...",
    "subject": "Team reward",
    "body": "Congratulations to top performers!",
    "targetType": "SELECTED_AGENTS",
    "totalRecipients": 2,
    "sentAt": "2026-06-05T22:28:48",
    "recipients": [
      {
        "id": "a1b2c3d4-...",
        "recipientType": "AGENT",
        "recipientId": "152c38f7-...",
        "name": "Agent Test",
        "phoneNumber": null,
        "email": "agenttest@ebee.com",
        "sentSms": false,
        "sentEmail": true
      },
      {
        "id": "e5f6g7h8-...",
        "recipientType": "AGENT",
        "recipientId": "adf3427f-...",
        "name": "New Agent2",
        "phoneNumber": "555-TEST2",
        "email": "agent-test2@ebee.com",
        "sentSms": true,
        "sentEmail": true
      }
    ]
  }
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Message identifier |
| `subject` | String | Message subject |
| `body` | String | Full message body |
| `targetType` | Enum | How recipients were targeted |
| `totalRecipients` | int | Number of recipients |
| `sentAt` | Timestamp | When the message was sent |
| `recipients[].id` | UUID | Recipient record ID |
| `recipients[].recipientType` | Enum | `AGENT`, `CUSTOMER`, or `MANUAL` |
| `recipients[].recipientId` | UUID | FK to `agents.id` or `customers.id` |
| `recipients[].name` | String | Recipient display name |
| `recipients[].phoneNumber` | String | Phone number for SMS |
| `recipients[].email` | String | Email address |
| `recipients[].sentSms` | Boolean | Whether SMS was delivered |
| `recipients[].sentEmail` | Boolean | Whether email was delivered |

---

## Error Responses

| HTTP Status | Scenario |
|-------------|----------|
| 400 | Missing `recipientIds` for `SELECTED_*` target, or missing `manualRecipients` for `MANUAL_ENTRY` |
| 401 | Missing or invalid JWT |
| 403 | Non-ADMIN role |
| 404 | Message not found |
| 403 | Accessing another admin's message |
| 500 | Internal server error |

---

## Delivery Behavior

- **Agent recipients** receive both SMS and email (if phone/email available)
- **Customer recipients** receive both SMS and email (if phone/email available)
- **Manual recipients** receive both SMS and email (if phone/email available)
- Delivery happens **asynchronously** after the HTTP response returns
- The `sentSms` / `sentEmail` flags are updated once the SMS/email sender responds
- If a recipient has no phone number, SMS is skipped (`sentSms: false`)
- If a recipient has no email address, email is skipped (`sentEmail: false`)
- The admin can check delivery status by querying the message detail endpoint

---

## Frontend Integration

| UI Screen | API Call |
|-----------|----------|
| **New message form** | `POST /api/messages` with target type and optional IDs |
| **Sent messages list** | `GET /api/messages/sent?page=0&size=20` |
| **Message detail (with recipients)** | `GET /api/messages/{id}` |

**Workflow for selected agents:**
1. Admin opens agent picker → `GET /api/agents`
2. Admin selects agents → collects their UUIDs
3. Admin fills in subject + body → `POST /api/messages` with `SELECTED_AGENTS` + selected UUIDs
4. Frontend redirects to sent history → `GET /api/messages/sent`
