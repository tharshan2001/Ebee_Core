# Ebee API Documentation

## Overview

Ebee is a commission-based order management system for Sri Lanka. This API covers order processing, agent management with district-based registration, commission calculation, customer management, and agent application intake.

---

## Base URL

```
http://localhost:8085/api
```

---

## Authentication

All endpoints (except public ones) require a JWT Bearer token:

```
Authorization: Bearer <JWT_TOKEN>
```

**Public endpoints** (no auth required):
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/agent-applications`

See [Authentication API](AUTH_API.md) for login, refresh, and token details.

---

## API Modules

| Module | File | Auth |
|--------|------|------|
| Authentication | [AUTH_API.md](AUTH_API.md) | Public (login/refresh) |
| Products | [PRODUCT_API.md](PRODUCT_API.md) | ADMIN |
| Agents | [AGENT_API.md](AGENT_API.md) | ADMIN (create/update/delete), any (read) |
| Agent Applications | [AGENT_APPLICATION_API.md](AGENT_APPLICATION_API.md) | Public (submit), ADMIN (manage) |
| Customers | [CUSTOMER_API.md](CUSTOMER_API.md) | ADMIN, AGENT |
| Orders | [ORDER_API.md](ORDER_API.md) | ADMIN, AGENT |
| Categories | [CATEGORY_API.md](CATEGORY_API.md) | ADMIN (mutate), any (read) |
| Commissions | [COMMISSION_API.md](COMMISSION_API.md) | ADMIN (mutate), any (read) |
| Commission Configs | [COMMISSION_API.md](COMMISSION_API.md#commission-configs) | ADMIN |

---

## Quick Start

### 1. Login as Admin

```bash
curl -X POST http://localhost:8085/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@ebee.com","password":"admin123"}'
```

Save the `token` from the response for subsequent requests.

### 2. Get Active Products

```bash
curl http://localhost:8085/api/products/active \
  -H "Authorization: Bearer <TOKEN>"
```

### 3. Get All Agents

```bash
curl http://localhost:8085/api/agents \
  -H "Authorization: Bearer <TOKEN>"
```

### 4. Create an Order

```bash
curl -X POST http://localhost:8085/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{
    "agentCode": "JF-008",
    "customerPhoneNumber": "+94771234567",
    "items": [{"productSku": "PA-001", "quantity": 2}]
  }'
```

### 5. Update Order Status

```bash
curl -X PATCH "http://localhost:8085/api/orders/<ORDER_ID>/status?status=CONFIRMED" \
  -H "Authorization: Bearer <TOKEN>"
```

---

## Standard Response Format

All endpoints return a consistent JSON envelope:

```json
{
  "success": true,
  "message": "Operation result description",
  "data": { ... }
}
```

**Error responses:**
```json
{
  "success": false,
  "message": "Error description",
  "data": null
}
```

**Common HTTP status codes:**

| Code | Description |
|------|-------------|
| 200 | Success |
| 201 | Created |
| 400 | Bad request / Validation error / Illegal state |
| 401 | Unauthorized — missing or invalid token |
| 403 | Forbidden — insufficient role |
| 404 | Resource not found |
| 500 | Internal server error |

---

## Sample Data

| Resource | Identifier | Notes |
|----------|-----------|-------|
| Admin | `admin@ebee.com` / `admin123` | Must change password on first login |
| Agent | `JF-008` | PERFORMANCE type, rate 7.5% |
| Agent | `JF-009` | DEFAULT type, rate 5.0% |
| Product | `PA-001` | $60.00, active, stock ~125 |
| Customer | `+94771234567` | Ranil Wickramasinghe |
| Default config | — | 7.5% global rate for DEFAULT agents |

---

## Env Configuration

All secrets are loaded from environment variables. See `.env` (gitignored) for local development values.

Required vars: `GMAIL_USERNAME`, `GMAIL_APP_PASSWORD`, `DIALOG_SMS_USERNAME`, `DIALOG_SMS_PASSWORD`, `JWT_SECRET`, `DB_PASSWORD`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`.
