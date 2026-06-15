# Ebee API Documentation

## Overview

Ebee is a POS (Point-of-Sale) and Commission Management System for Sri Lanka. It covers order processing, agent management, commission calculation, customer management, agent applications, expense tracking, financial reporting, inventory management, and delivery tracking with district-based routing and driver management.

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

**Public endpoints** (no auth required, rate-limited):
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/forgot-password`
- `POST /api/auth/verify-otp`
- `POST /api/auth/reset-password`
- `POST /api/agent-applications`
- `GET /api/products/**`
- `GET /api/categories/**`

See [Authentication API](AUTH_API.md) for login, refresh, and token details.

---

## API Modules

| Module | File | Auth |
|--------|------|------|
| Authentication | [AUTH_API.md](AUTH_API.md) | Public (login/refresh) |
| Users | [USER_API.md](USER_API.md) | ADMIN (mutate), any (read own) |
| Profile | [PROFILE_API.md](PROFILE_API.md) | Authenticated users |
| Products | [PRODUCT_API.md](PRODUCT_API.md) | ADMIN |
| Inventory | [INVENTORY_API.md](INVENTORY_API.md) | ADMIN (mutate), any (read) |
| Categories | [CATEGORY_API.md](CATEGORY_API.md) | ADMIN (mutate), any (read) |
| Agents | [AGENT_API.md](AGENT_API.md) | ADMIN (create/update/delete), any (read) |
| Agent Applications | [AGENT_APPLICATION_API.md](AGENT_APPLICATION_API.md) | Public (submit), ADMIN (manage) |
| Customers | [CUSTOMER_API.md](CUSTOMER_API.md) | ADMIN, AGENT |
| Orders | [ORDER_API.md](ORDER_API.md) | ADMIN, AGENT |
| Commissions | [COMMISSION_API.md](COMMISSION_API.md) | ADMIN (mutate), any (read) |
| Commission Configs | [COMMISSION_API.md](COMMISSION_API.md#commission-configs) | ADMIN |
| Delivery | [DELIVERY_API.md](DELIVERY_API.md) | ADMIN, DRIVER |
| Drivers | [DRIVER_API.md](DRIVER_API.md) | ADMIN, DRIVER |
| Expenses | [EXPENSE_API.md](EXPENSE_API.md) | ADMIN, AGENT, DRIVER |
| Finance | [FINANCE_API.md](FINANCE_API.md) | ADMIN |
| Alerts | [ALERT_API.md](ALERT_API.md) | ADMIN, AGENT |
| Messages | [MESSAGE_API.md](MESSAGE_API.md) | ADMIN |
| Reminders | [REMINDER_API.md](REMINDER_API.md) | ADMIN |
| Sales & Dashboard | [SALES_API.md](SALES_API.md) | ADMIN (most), AGENT (leaderboard) |
| Reports | [REPORTS_API.md](REPORTS_API.md) | ADMIN |
| Audit Logs | [ADMIN_AUDIT_API.md](ADMIN_AUDIT_API.md) | ADMIN |
| Database Backup | [ADMIN_BACKUP_API.md](ADMIN_BACKUP_API.md) | ADMIN |

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
    "items": [{"productItemCode": "PA-001", "quantity": 2}]
  }'
```

### 5. Update Order Status

```bash
curl -X PATCH "http://localhost:8085/api/orders/<ORDER_ID>/status?status=CONFIRMED" \
  -H "Authorization: Bearer <TOKEN>"
```

### 6. Ship Order (auto-inits delivery)

```bash
curl -X PATCH "http://localhost:8085/api/orders/<ORDER_ID>/status?status=SHIPPED" \
  -H "Authorization: Bearer <TOKEN>"
```

Order delivery status is auto-set to `PENDING`.

### 7. Assign Driver to Delivery

```bash
curl -X PATCH "http://localhost:8085/api/deliveries/<ORDER_ID>/assign" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{"driverId": "<DRIVER_USER_ID>"}'
```

Driver availability is auto-set to `ON_TRIP`.

### 8. Update Delivery Status

```bash
curl -X PATCH "http://localhost:8085/api/deliveries/<ORDER_ID>/status" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{"status": "DELIVERED"}'
```

Driver availability is auto-returned to `AVAILABLE` and order status is set to `DELIVERED`.

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
| 429 | Too many requests — rate limit exceeded |
| 500 | Internal server error |

---

## Sample Data

| Resource | Identifier | Notes |
|----------|-----------|-------|
| Admin | `admin@ebee.com` / `admin123` | Must change password on first login |
| Agent | `JF-008` | PERFORMANCE type, rate 7.5% |
| Agent | `JF-009` | DEFAULT type, rate 5.0% |
| Product | `PA-001` | $60.00, active, stock ~125 |
| Customer | `+94771234567` | Ranil Wickramasinghe, Vavuniya |
| Driver | `driver1@ebee.com` | Driver One, Colombo |
| Driver | `driver@test.com` | Driver Test, Kandy |
| Default config | — | 7.5% global rate for DEFAULT agents |

---

## Env Configuration

All secrets are loaded from environment variables (see `.env` in the project root). No secrets are stored in `application.yml` — if env vars are missing, the application starts with empty credentials rather than falling back to hardcoded values.

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/ebee_db` |
| `DB_USERNAME` | PostgreSQL username | `antonabitharshan` |
| `DB_PASSWORD` | PostgreSQL password | _(empty)_ |
| `DB_POOL_MAX_SIZE` | HikariCP max pool size | `10` |
| `DB_POOL_MIN_IDLE` | HikariCP min idle connections | `2` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_PASSWORD` | Redis password | _(empty)_ |
| `MAIL_HOST` | SMTP host | `smtp.gmail.com` |
| `MAIL_PORT` | SMTP port | `587` |
| `GMAIL_USERNAME` | Gmail SMTP username | _(empty)_ |
| `GMAIL_APP_PASSWORD` | Gmail app password | _(empty)_ |
| `AWS_ACCESS_KEY_ID` | AWS access key | _(empty)_ |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key | _(empty)_ |
| `AWS_REGION` | AWS region | `eu-north-1` |
| `AWS_BUCKET_NAME` | AWS S3 bucket | _(empty)_ |
| `JWT_SECRET` | JWT signing secret (256-bit) | _(empty)_ |
| `JWT_RESET_SECRET` | JWT reset-token signing secret | _(empty, falls back to JWT_SECRET)_ |
| `JWT_EXPIRATION` | Access token TTL (ms) | `86400000` (24h) |
| `JWT_REFRESH_EXPIRATION` | Refresh token TTL (ms) | `604800000` (7d) |
| `DIALOG_SMS_API_URL` | Dialog SMS API base URL | _(empty)_ |
| `DIALOG_SMS_USERNAME` | Dialog SMS API username | _(empty)_ |
| `DIALOG_SMS_PASSWORD` | Dialog SMS API password | _(empty)_ |
| `DIALOG_SMS_MASK` | SMS sender mask | `KISMET PLUS` |
| `DIALOG_SMS_CAMPAIGN` | SMS campaign name | `Notification` |
| `SERVER_PORT` | HTTP server port | `8085` |
