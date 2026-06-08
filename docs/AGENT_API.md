# Agent API Reference

## Base URL

```
http://localhost:8085/api/agents
```

---

## Supported Districts

Agents are assigned to one of these North & East Sri Lanka districts. The district determines the auto-generated agent code prefix.

| Code | District |
|------|----------|
| JF | Jaffna |
| KN | Kilinochchi |
| MN | Mannar |
| ML | Mullaitivu |
| VN | Vavuniya |
| TM | Trincomalee |
| BC | Batticaloa |
| AP | Ampara |

**Agent code format:** `{PREFIX}-{SEQ}` (e.g., `BC-003`, `JF-001`). Sequences are per-district — each district starts at `001`.

`district` is validated against the enum — creating or updating with an invalid value returns 400 with `"Invalid district: ..."`. The check is case-insensitive (e.g. `"jaffna"` or `"Jaffna"` both work).

---

## Commission Types

| Type | Behavior |
|------|----------|
| `DEFAULT` | Agent's rate comes from the global default commission config. |
| `PERFORMANCE` | Config lookup is skipped. Agent uses their own `commissionRate` directly. |

**On creation**, agents are always `DEFAULT` type with the rate auto-populated from the active default commission config. The admin can change to `PERFORMANCE` and set a custom rate later via PATCH.

See [Commission API](COMMISSION_API.md) for config management.

---

## Welcome Notification

When an admin creates an agent, the system automatically sends a welcome notification via **email** and **SMS** containing their login credentials (agent code, email, auto-generated password).

> Notification failures are logged as warnings and do **not** block agent creation.

---

## Endpoints

### Create Agent

**POST** `/api/agents`

**Access:** ADMIN only

**Headers:**
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: multipart/form-data
```

**Form Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| email | string | yes | Agent's email (unique) |
| firstName | string | yes | First name |
| lastName | string | yes | Last name |
| district | string | yes | District name (see table above) |
| address | string | no | Agent's address |
| phoneNumber | string | no | Phone number |
| nicNumber | string | no | NIC number |
| nicDocument | file | no | NIC document (PDF/image) |
| bankPassbookDocument | file | no | Bank passbook document (PDF/image) |
| bankAccountNumber | string | no | Bank account number |
| bankName | string | no | Bank name |
| bankBranch | string | no | Bank branch |

> `commissionRate` is auto-populated from the active default commission config. `commissionType` is always `DEFAULT` on creation. Switch to `PERFORMANCE` via PATCH to set a custom rate.

**cURL Example:**
```bash
curl -X POST http://localhost:8085/api/agents \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -F "email=agent@example.com" \
  -F "firstName=John" \
  -F "lastName=Doe" \
  -F "address=123 Main Street, Jaffna" \
  -F "district=Jaffna" \
  -F "phoneNumber=0771234567"
```

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Agent created successfully",
  "data": {
    "agentId": "uuid",
    "userId": "uuid",
    "email": "agent@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "agentCode": "JF-001",
    "address": "123 Main Street, Jaffna",
    "district": "Jaffna",
    "phoneNumber": "0771234567",
    "commissionRate": 7.5,
    "commissionType": "DEFAULT",
    "nicNumber": null,
    "bankAccountNumber": null,
    "bankName": null,
    "bankBranch": null,
    "userStatus": "ACTIVE",
    "isActive": true,
    "mustChangePassword": false,
    "createdAt": "2026-06-02T23:17:32.567441",
    "generatedPassword": "5nFy$Z9U$YWB"
  }
}
```

> `agentCode` is auto-generated. `generatedPassword` is only returned in this create response.

---

### Get All Agents

**GET** `/api/agents`

**Access:** ADMIN only

**Response:**
```json
{
  "success": true,
  "message": "Agents retrieved successfully",
  "data": [
    {
      "id": "uuid",
      "userId": "uuid",
      "email": "agent@example.com",
      "firstName": "John",
      "lastName": "Doe",
      "fullName": "John Doe",
      "agentCode": "JF-001",
      "address": "123 Main Street, Jaffna",
      "district": "Jaffna",
      "phoneNumber": "0771234567",
      "commissionRate": 7.5,
      "commissionType": "DEFAULT",
      "totalSales": 0.0,
      "totalCommission": 0.0,
      "isActive": true,
      "userStatus": "ACTIVE",
      "nicNumber": null,
      "nicDocumentUrl": null,
      "nicDocumentName": null,
      "nicDocumentType": null,
      "bankPassbookDocumentUrl": null,
      "bankPassbookDocumentName": null,
      "bankPassbookDocumentType": null,
      "bankAccountNumber": null,
      "bankName": null,
      "bankBranch": null,
      "createdAt": "2026-06-02T23:17:32.567441",
      "updatedAt": "2026-06-02T23:17:32.567441"
    }
  ]
}
```

---

### Get Agent by ID

**GET** `/api/agents/{id}`

**Access:** Any authenticated user

**Response:** Same as single item in Get All response.

---

### Get Agent by Code

**GET** `/api/agents/code/{agentCode}`

**Access:** Any authenticated user

**Response:** Same as single item in Get All response.

---

### Update Agent

**PATCH** `/api/agents/{id}`

**Access:** ADMIN only

**Headers:**
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: multipart/form-data
```

**Form Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| agentCode | string | yes | Agent code |
| commissionRate | number | yes | Commission rate (percentage) |
| commissionType         | string   | no       | `DEFAULT` or `PERFORMANCE` |
| district | string | no | Change district |
| address | string | no | Change address |
| phoneNumber | string | no | Change phone number |
| nicNumber | string | no | NIC number |
| nicDocument | file | no | NIC document (replaces existing) |
| bankPassbookDocument | file | no | Bank passbook (replaces existing) |
| bankAccountNumber | string | no | Bank account number |
| bankName | string | no | Bank name |
| bankBranch | string | no | Bank branch |

**cURL Example:**
```bash
curl -X PATCH http://localhost:8085/api/agents/{id} \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -F "agentCode=JF-001" \
  -F "commissionRate=10.0" \
  -F "commissionType=PERFORMANCE" \
  -F "address=456 New Road" \
  -F "district=Kilinochchi" \
  -F "phoneNumber=0777654321"
```

---

### Update Commission Rate Only

**PATCH** `/api/agents/{id}/commission-rate`

**Access:** ADMIN only

**Parameters:** `commissionRate=10.0`

```bash
curl -X PATCH "http://localhost:8085/api/agents/{id}/commission-rate?commissionRate=10.0" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

### Activate / Deactivate Agent

**PATCH** `/api/agents/{id}/activate`
**PATCH** `/api/agents/{id}/deactivate`

**Access:** ADMIN only

---

### Delete Agent (Soft)

**DELETE** `/api/agents/{id}`

**Access:** ADMIN only

Sets agent to inactive. Does not permanently remove the record.

```bash
curl -X DELETE http://localhost:8085/api/agents/{id} \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response:**
```json
{
  "success": true,
  "message": "Agent deleted successfully"
}
```

---

### Reset Agent Password

**POST** `/api/agents/{id}/reset-password`

**Access:** ADMIN only

Resets the agent's password to a new randomly generated value. The new password is sent to the agent's email. The agent will be forced to change their password on next login (`mustChangePassword = true`).

```bash
curl -X POST http://localhost:8085/api/agents/{id}/reset-password \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response:**
```json
{
  "success": true,
  "message": "Password reset successfully. The new password has been sent to the agent's email."
}
```
