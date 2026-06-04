# Sales & Dashboard API

**Base URL:** `http://localhost:8085/api/sales`

---

## Authentication

All endpoints require JWT:
```
Authorization: Bearer <JWT_TOKEN>
```

**Role:** `ADMIN` only — all sales endpoints require admin access.

---

## Data Source

All sales figures are derived from the **`orders`** table. Orders with status `CANCELLED` or `RETURNED` are excluded from every aggregation.

| Metric | Source |
|--------|--------|
| Revenue | `orders.total_amount` |
| Order count | `orders` row count |
| Agent breakdown | `orders.agent_id` → `agents` → `users` |
| Product breakdown | `order_items.subtotal` + `products` |
| Category breakdown | `order_items.subtotal` + `products.category` |
| Commission totals | `commissions.commission_amount` (excludes `CANCELLED`/`REJECTED`) |
| Deliveries count | `orders.delivered_at` where `status = 'DELIVERED'` |

---

## Common Parameters

All sales endpoints (except `/dashboard`) accept these optional query parameters:

| Param | Type | Format | Description |
|-------|------|--------|-------------|
| `startDate` | `LocalDate` | `yyyy-MM-dd` | Include orders from this date (inclusive) |
| `endDate` | `LocalDate` | `yyyy-MM-dd` | Include orders up to this date (inclusive) |

When omitted, all matching orders (regardless of date) are included.

**Example curl with date filter:**
```bash
curl -s "http://localhost:8085/api/sales/summary?startDate=2026-01-01&endDate=2026-06-30" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

## Dashboard Endpoint

Single call returning all KPI data for the admin dashboard, including current vs previous period comparison and today/yesterday snapshots.

### GET /api/sales/dashboard

**Access:** ADMIN only

**Query Parameters:**

| Param | Type | Format | Description |
|-------|------|--------|-------------|
| `startDate` | `LocalDate` | `yyyy-MM-dd` | Start of the current period (optional) |
| `endDate` | `LocalDate` | `yyyy-MM-dd` | End of the current period (optional) |

When neither is provided, defaults to **last 30 days** ending today. The previous period is automatically computed as the same length immediately before the current period.

**Response Structure:**

```
DashboardResponse
├── period:            { start, end }                              — the requested/calculated date range
├── summary:           { totalRevenue, totalOrders,                — current period metrics
│                        averageOrderValue, totalCommission }
├── today:             { ordersToday, ordersYesterday,            — today vs yesterday KPIs
│                        deliveriesToday, deliveriesYesterday }
└── previousPeriod:    { totalRevenue, totalOrders,               — same-length prior period
                         totalCommission }
```

**Response Fields Detail:**

| Field | Type | Description |
|-------|------|-------------|
| `period.start` | `LocalDate` | Start of the current period |
| `period.end` | `LocalDate` | End of the current period |
| `summary.totalRevenue` | `BigDecimal` | Revenue in current period (excl cancelled/returned) |
| `summary.totalOrders` | `Long` | Order count in current period (excl cancelled/returned) |
| `summary.averageOrderValue` | `BigDecimal` | Revenue / orders |
| `summary.totalCommission` | `BigDecimal` | Commission locked in current period (excl cancelled/rejected) |
| `today.ordersToday` | `Long` | Total orders placed today (all statuses) |
| `today.ordersYesterday` | `Long` | Total orders placed yesterday (all statuses) |
| `today.deliveriesToday` | `Long` | Orders delivered today |
| `today.deliveriesYesterday` | `Long` | Orders delivered yesterday |
| `previousPeriod.totalRevenue` | `BigDecimal` | Revenue in the prior period |
| `previousPeriod.totalOrders` | `Long` | Order count in the prior period |
| `previousPeriod.totalCommission` | `BigDecimal` | Commission locked in the prior period |

**Example curl:**
```bash
curl -s http://localhost:8085/api/sales/dashboard \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Example Response:**
```json
{
  "success": true,
  "message": "Dashboard data retrieved successfully",
  "data": {
    "period": {
      "start": "2026-05-05",
      "end": "2026-06-04"
    },
    "summary": {
      "totalRevenue": 174924.18,
      "totalOrders": 94,
      "averageOrderValue": 1860.90,
      "totalCommission": 15002.86
    },
    "today": {
      "ordersToday": 8,
      "ordersYesterday": 8,
      "deliveriesToday": 3,
      "deliveriesYesterday": 3
    },
    "previousPeriod": {
      "totalRevenue": 110000.00,
      "totalOrders": 80,
      "totalCommission": 9000.00
    }
  }
}
```

**Frontend usage:**
- `summary.totalRevenue` vs `previousPeriod.totalRevenue` → compute % change
- `today.ordersToday` vs `today.ordersYesterday` → compute % change
- `today.deliveriesToday` vs `today.deliveriesYesterday` → compute % change

---

## Sales Summary

### GET /api/sales/summary

**Access:** ADMIN

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `totalRevenue` | `BigDecimal` | Sum of `total_amount` across all qualifying orders |
| `totalOrders` | `Long` | Count of qualifying orders |
| `averageOrderValue` | `BigDecimal` | `totalRevenue / totalOrders` (2 decimal precision) |
| `totalCommission` | `BigDecimal` | Sum of commission amounts (excl cancelled/rejected) |
| `periodStart` | `LocalDateTime` | The `startDate` filter value (null if not provided) |
| `periodEnd` | `LocalDateTime` | The `endDate` filter value (null if not provided) |

**Example Response:**
```json
{
  "success": true,
  "message": "Sales summary retrieved successfully",
  "data": {
    "totalRevenue": 1250000.00,
    "totalOrders": 45,
    "averageOrderValue": 27777.78,
    "totalCommission": 12450.00,
    "periodStart": "2026-01-01T00:00:00",
    "periodEnd": "2026-12-31T00:00:00"
  }
}
```

**Example curl:**
```bash
curl -s http://localhost:8085/api/sales/summary \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

## Sales By Agent

### GET /api/sales/by-agent

Revenue and order count grouped by agent.

**Access:** ADMIN

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `agentId` | `UUID` | Agent's unique ID |
| `agentCode` | `String` | Agent's code (e.g., `JF-001`, `CM-003`) |
| `agentName` | `String` | Agent's full name |
| `totalOrders` | `Long` | Number of orders assigned to this agent |
| `totalRevenue` | `BigDecimal` | Sum of order amounts for this agent |

**Example curl:**
```bash
curl -s "http://localhost:8085/api/sales/by-agent?startDate=2026-06-01" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

## Sales By Product

### GET /api/sales/by-product

Quantity sold and revenue grouped by product, sorted by revenue descending.

**Access:** ADMIN

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `productId` | `UUID` | Product's unique ID |
| `productName` | `String` | Product name |
| `itemCode` | `String` | Product item code |
| `category` | `String` | Product category |
| `totalQuantity` | `Long` | Total units sold |
| `totalRevenue` | `BigDecimal` | Sum of `subtotal` across all order items |

**Example curl:**
```bash
curl -s http://localhost:8085/api/sales/by-product \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

## Sales By Category

### GET /api/sales/by-category

Revenue, quantity, and percentage share grouped by product category.

**Access:** ADMIN

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `category` | `String` | Product category name |
| `totalQuantity` | `Long` | Total units sold in this category |
| `totalRevenue` | `BigDecimal` | Sum of subtotals for products in this category |
| `percentageOfTotal` | `BigDecimal` | This category's share of total revenue (0–100%) |

**Example curl:**
```bash
curl -s "http://localhost:8085/api/sales/by-category?startDate=2026-01-01" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

## Agent Sales Detail

### GET /api/sales/agent/{agentId}

Detailed sales and commission data for a single agent.

**Access:** ADMIN

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `agentId` | `UUID` | The agent's unique ID |

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `agentId` | `UUID` | Agent's unique ID |
| `agentCode` | `String` | Agent's code |
| `agentName` | `String` | Agent's full name |
| `totalOrders` | `Long` | Number of orders (non-cancelled, non-returned) |
| `totalRevenue` | `BigDecimal` | Sum of order amounts |
| `totalCommission` | `BigDecimal` | All-time commission total (excludes cancelled/rejected commissions) |

**Example curl:**
```bash
curl -s "http://localhost:8085/api/sales/agent/81c662e0-80e0-450d-b492-adc1c15103c7" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

## Trend Endpoints

### GET /api/sales/trend/daily
### GET /api/sales/trend/monthly
### GET /api/sales/trend/yearly

Revenue and order count broken down by day, month, or year.

**Access:** ADMIN

**Response Fields (all trend endpoints):**

| Field | Type | Description |
|-------|------|-------------|
| `period` | `LocalDateTime` | Start of the period (`DATE_TRUNC` of day/month/year) |
| `totalOrders` | `Long` | Orders in this period |
| `totalRevenue` | `BigDecimal` | Revenue for this period |

**Example curl:**
```bash
curl -s "http://localhost:8085/api/sales/trend/daily?startDate=2026-06-01&endDate=2026-06-30" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Example Response (daily):**
```json
{
  "success": true,
  "message": "Daily sales trend retrieved successfully",
  "data": [
    { "period": "2026-06-02T00:00:00", "totalOrders": 3, "totalRevenue": 180.00 },
    { "period": "2026-06-03T00:00:00", "totalOrders": 5, "totalRevenue": 360.00 }
  ]
}
```

---

## Error Responses

All endpoints return standard error format on failure:

```json
{
  "success": false,
  "message": "An unexpected error occurred: <details>",
  "data": null
}
```

Common causes:
- **403** — Missing or invalid JWT token, or non-ADMIN role
- **500** — Database connection issue, or unexpected query failure
