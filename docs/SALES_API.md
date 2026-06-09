# Sales & Dashboard API

**Base URL:** `http://localhost:8085/api/sales`

---

## Authentication

All endpoints require JWT:
```
Authorization: Bearer <JWT_TOKEN>
```

**Role:** Most endpoints require `ADMIN`. The leaderboard endpoints also allow `AGENT`.

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

## Leaderboard

### GET /api/sales/leaderboard

Ranked list of agents by sales performance. Includes **all active agents** (agents with zero orders appear at the bottom with `totalOrders: 0` and `totalRevenue: 0`).

**Access:** ADMIN, AGENT

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `metric` | `String` | `revenue` | Sort metric: `revenue`, `orders`, `commission`, or `avgOrderValue` |
| `limit` | `int` | `10` | Maximum agents to return (`0` = no limit, returns all) |
| `district` | `District` | — | Filter by district enum value (optional) |
| `startDate` | `LocalDate` | — | Include orders from this date (optional) |
| `endDate` | `LocalDate` | — | Include orders up to this date (optional) |

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `rank` | `int` | Position in sorted leaderboard (1 = best) |
| `agentId` | `UUID` | Agent's unique ID |
| `agentCode` | `String` | Agent's code (e.g., `JF-001`) |
| `agentName` | `String` | Agent's full name |
| `district` | `String` | Agent's district (e.g., `MN`, `JF`) |
| `totalOrders` | `Long` | Number of qualifying orders |
| `totalRevenue` | `BigDecimal` | Sum of order amounts |
| `totalCommission` | `BigDecimal` | Sum of commission amounts in the period |
| `avgOrderValue` | `BigDecimal` | `totalRevenue / totalOrders` (or `0` for zero-order agents) |
| `currentUser` | `boolean` | Always `false` in the general leaderboard |

**Example curl:**
```bash
# Top 5 by revenue
curl -s "http://localhost:8085/api/sales/leaderboard?limit=5" \
  -H "Authorization: Bearer <JWT_TOKEN>"

# Top 10 by commission in a date range
curl -s "http://localhost:8085/api/sales/leaderboard?metric=commission&limit=10&startDate=2026-01-01&endDate=2026-06-30" \
  -H "Authorization: Bearer <JWT_TOKEN>"

# All agents in MN district sorted by orders
curl -s "http://localhost:8085/api/sales/leaderboard?metric=orders&limit=0&district=MN" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Example Response:**
```json
{
  "success": true,
  "message": "Leaderboard retrieved successfully",
  "data": [
    {
      "rank": 1,
      "agentId": "05cef7aa-ac7c-4498-bd7b-dc51d9acddd5",
      "agentCode": "MN-006",
      "agentName": "Anton Abitharshan",
      "district": "MN",
      "totalOrders": 5,
      "totalRevenue": 260209.98,
      "totalCommission": 19515.00,
      "avgOrderValue": 52042.00,
      "currentUser": false
    }
  ]
}
```

---

### GET /api/sales/leaderboard/my-rank

Returns the **full leaderboard** (no limit) with the currently authenticated agent marked by `currentUser: true`. Useful for showing the logged-in agent where they stand among all peers.

**Access:** AGENT only (ADMIN gets 403)

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `metric` | `String` | `revenue` | Sort metric: `revenue`, `orders`, `commission`, or `avgOrderValue` |
| `district` | `District` | — | Filter by district (optional) |
| `startDate` | `LocalDate` | — | Include orders from this date (optional) |
| `endDate` | `LocalDate` | — | Include orders up to this date (optional) |

**Note:** The `limit` parameter is **not** available here — the full leaderboard is always returned so the agent can see their exact position.

**Example curl:**
```bash
curl -s "http://localhost:8085/api/sales/leaderboard/my-rank" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Example Response:**
```json
{
  "success": true,
  "message": "My rank retrieved successfully",
  "data": [
    { "rank": 1, "agentId": "05cef7aa-...", "agentName": "Top Agent", "currentUser": false, ... },
    { "rank": 2, "agentId": "adf3427f-...", "agentName": "Second Agent", "currentUser": false, ... },
    { "rank": 5, "agentId": "05cef7aa-...", "agentName": "Anton Abitharshan", "currentUser": true, ... }
  ]
}
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
