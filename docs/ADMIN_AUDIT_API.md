# Admin Audit Log API

**Base URL:** `http://localhost:8085/api/admin/audit-logs`

## Authentication

All endpoints require JWT with `ADMIN` role:

```
Authorization: Bearer <JWT_TOKEN>
```

Non-admin users receive `403 Forbidden`.

---

## List Audit Logs

**GET** `/api/admin/audit-logs`

Returns paginated audit logs with optional filters.

**Query Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `entityType` | `String` | No | Filter by entity (e.g., `Order`, `User`, `Category`) |
| `entityId` | `UUID` | No | Filter by specific record |
| `action` | `String` | No | Filter by action (`CREATE`, `LOGIN`, `STATUS_CHANGE`, etc.) |
| `performedBy` | `UUID` | No | Filter by user who performed the action |
| `startDate` | `ISO DateTime` | No | From date (e.g., `2026-06-01T00:00:00`) |
| `endDate` | `ISO DateTime` | No | To date (e.g., `2026-06-09T23:59:59`) |
| `page` | `int` | No | Page number (default: `0`) |
| `size` | `int` | No | Page size (default: `50`) |

**Example:**
```bash
curl "http://localhost:8085/api/admin/audit-logs?entityType=Order&action=CREATE&page=0&size=20" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response (200):**
```json
{
  "success": true,
  "message": "Audit logs retrieved",
  "data": {
    "content": [
      {
        "id": "5ed27d07-c957-4193-8ab1-7c837e851d89",
        "entityType": "User",
        "entityId": "7a5a36c9-8423-464c-8697-9b30f0025ce0",
        "action": "LOGIN",
        "summary": "User admin@ebee.lk logged in as ADMIN",
        "performedBy": "7a5a36c9-8423-464c-8697-9b30f0025ce0",
        "performedByEmail": "admin@ebee.lk",
        "performedByRole": "ADMIN",
        "ipAddress": null,
        "createdAt": "2026-06-09T22:34:41.979"
      }
    ],
    "pageable": { ... },
    "totalElements": 42,
    "totalPages": 3,
    ...
  }
}
```

---

## Get Audit Log Detail

**GET** `/api/admin/audit-logs/{id}`

Returns a single audit log entry by ID.

**Example:**
```bash
curl "http://localhost:8085/api/admin/audit-logs/5ed27d07-c957-4193-8ab1-7c837e851d89" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response (200):**
```json
{
  "success": true,
  "message": "Audit log retrieved",
  "data": {
    "id": "5ed27d07-c957-4193-8ab1-7c837e851d89",
    "entityType": "User",
    "entityId": "7a5a36c9-8423-464c-8697-9b30f0025ce0",
    "action": "LOGIN",
    "summary": "User admin@ebee.lk logged in as ADMIN",
    "performedBy": "7a5a36c9-8423-464c-8697-9b30f0025ce0",
    "performedByEmail": "admin@ebee.lk",
    "performedByRole": "ADMIN",
    "ipAddress": null,
    "createdAt": "2026-06-09T22:34:41.979"
  }
}
```

**Error (404):** Log not found.

---

## How Audit Works

### Auto-Capture (AOP)

A Spring AOP aspect wraps all `CrudRepository.save()` and `delete()` method calls. Every entity create or update is automatically logged:

- **CREATE** detected when `entity.getId()` is `null` before `save()`
- **UPDATE** detected when `entity.getId()` is not `null` before `save()`
- **DELETE** logged on `repository.delete(entity)`

**Excluded from auto-capture:** `AuditLog` entities (prevents recursion). A `ThreadLocal` guard prevents nested audit calls within the same request.

### Manual Capture

Specific business actions are manually recorded in service classes:

| Service | Actions Recorded |
|---------|-----------------|
| `AuthService` | `LOGIN`, `LOGOUT`, `PASSWORD_CHANGE`, `FORGOT_PASSWORD`, `PASSWORD_RESET` |
| `OrderService` | `CREATE`, `STATUS_CHANGE` |
| `DeliveryService` | `DELIVERY_ASSIGN`, `DELIVERY_STATUS_CHANGE` |
| `CommissionLifecycleService` | `COMMISSION_APPROVE`, `COMMISSION_PROCESS`, `COMMISSION_PAY`, `COMMISSION_REJECT`, `COMMISSION_CANCEL` |
| `AgentService` | `CREATE`, `AGENT_APPROVE`, `AGENT_REJECT`, `AGENT_ACTIVATE`, `AGENT_DEACTIVATE`, `PASSWORD_RESET` |
| `DriverService` | `DRIVER_CREATE`, `DRIVER_UPDATE`, `DRIVER_AVAILABILITY_CHANGE` |

### Anonymous Contexts

When audit recording occurs in a context without an authenticated user (scheduled tasks, async operations), the performer defaults to:
```json
{
  "performedBy": null,
  "performedByEmail": "system",
  "performedByRole": "SYSTEM"
}
```

### AuditAction Enum

| Category | Values |
|----------|--------|
| CRUD | `CREATE`, `UPDATE`, `DELETE` |
| Auth | `LOGIN`, `LOGOUT`, `PASSWORD_RESET`, `PASSWORD_CHANGE`, `FORGOT_PASSWORD` |
| Orders | `STATUS_CHANGE`, `DELIVERY_ASSIGN`, `DELIVERY_STATUS_CHANGE` |
| Commissions | `COMMISSION_APPROVE`, `COMMISSION_PROCESS`, `COMMISSION_PAY`, `COMMISSION_CANCEL`, `COMMISSION_REJECT` |
| Agents | `AGENT_APPROVE`, `AGENT_REJECT`, `AGENT_ACTIVATE`, `AGENT_DEACTIVATE` |
| Drivers | `DRIVER_CREATE`, `DRIVER_UPDATE`, `DRIVER_AVAILABILITY_CHANGE` |
