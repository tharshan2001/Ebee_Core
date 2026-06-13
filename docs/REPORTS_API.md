# Reports API Reference

**Base URL:** `http://localhost:8085/api/reports`

All 27 report endpoints are grouped under `/api/reports` and provide period-aware analytics across every domain. Every endpoint is admin-only (`ADMIN` role required).

---

## Authentication

```
Authorization: Bearer <JWT_TOKEN>
```

**Role:** `ADMIN` only for all endpoints.

---

## Period System

Every endpoint accepts these optional query parameters:

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `period` | `String` | `this_month` | Named period (see table below) |
| `startDate` | `LocalDate` | — | Custom start date (ISO: `YYYY-MM-DD`). Overrides `period` when both `startDate` and `endDate` are provided. |
| `endDate` | `LocalDate` | — | Custom end date (ISO: `YYYY-MM-DD`). Overrides `period` when both `startDate` and `endDate` are provided. |

### Period Values

| Value | Range |
|-------|-------|
| `today` | Current day |
| `yesterday` | Previous day |
| `this_week` | Monday → today |
| `last_week` | Previous Monday → previous Sunday |
| `this_month` | 1st of month → today |
| `last_month` | 1st of previous month → last day of previous month |
| `this_quarter` | 1st of current quarter → today |
| `last_quarter` | 1st of previous quarter → last day of previous quarter |
| `this_year` | 1st Jan → today |
| `last_year` | 1st Jan of previous year → 31st Dec of previous year |
| `all` | `2020-01-01` → today |

If `startDate` and `endDate` are both provided, the `period` parameter is ignored.

---

## Sales Reports

### GET /api/reports/sales/summary

Period-based revenue/orders summary with previous-period comparison.

**Example:**
```bash
curl -s "http://localhost:8085/api/reports/sales/summary?period=this_month" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response:**
```json
{
  "success": true,
  "message": "Sales summary retrieved",
  "data": {
    "totalRevenue": 545850.31,
    "totalOrders": 62,
    "averageOrderValue": 8804.04,
    "totalCommission": 40753.59,
    "period": { "startDate": "2026-06-01", "endDate": "2026-06-10" },
    "previousPeriod": {
      "totalRevenue": 47624.50,
      "totalOrders": 27,
      "totalCommission": 3975.57,
      "revenueChangePercent": 1046.15,
      "ordersChangePercent": 129.63
    }
  }
}
```

---

### GET /api/reports/sales/trend

Time-series breakdown with configurable granularity.

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `granularity` | `String` | `monthly` | `daily`, `weekly`, `monthly`, or `yearly` |

**Example:**
```bash
curl -s "http://localhost:8085/api/reports/sales/trend?period=this_month&granularity=daily" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response:**
```json
{
  "success": true,
  "message": "Sales trend retrieved",
  "data": [
    { "period": "2026-06-01", "totalOrders": 1, "totalRevenue": 649.87, "totalCommission": null },
    { "period": "2026-06-02", "totalOrders": 4, "totalRevenue": 889.96, "totalCommission": null }
  ]
}
```

---

### GET /api/reports/sales/by-source

Revenue split between AGENT and DIRECT order sources.

**Example:**
```bash
curl -s "http://localhost:8085/api/reports/sales/by-source?period=all" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response:**
```json
{
  "success": true,
  "message": "Sales by source retrieved",
  "data": {
    "agent": { "totalRevenue": 677869.21, "totalOrders": 108, "percentageOfRevenue": 98.58 },
    "direct": { "totalRevenue": 9729.77, "totalOrders": 19, "percentageOfRevenue": 1.42 }
  }
}
```

---

### GET /api/reports/sales/by-status

Orders and revenue grouped by order status.

**Response:**
```json
{
  "success": true,
  "message": "Sales by status retrieved",
  "data": [
    { "status": "DELIVERED", "count": 80, "totalRevenue": 417431.32, "percentage": 62.02 },
    { "status": "PENDING", "count": 29, "totalRevenue": 267847.67, "percentage": 22.48 }
  ]
}
```

---

## Order Reports

### GET /api/reports/orders/summary

Order volume breakdown by status + cancellation rate.

**Response:**
```json
{
  "success": true,
  "message": "Order summary retrieved",
  "data": {
    "totalOrders": 124,
    "pendingCount": 29,
    "shippedCount": 13,
    "deliveredCount": 80,
    "cancelledCount": 2,
    "returnedCount": 0,
    "cancellationRate": 1.61,
    "period": { "startDate": "2020-01-01", "endDate": "2026-06-10" }
  }
}
```

---

### GET /api/reports/orders/top-customers

Top customers by total spend.

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `limit` | `int` | `10` | Number of customers to return |

**Response:**
```json
{
  "success": true,
  "message": "Top customers retrieved",
  "data": [
    {
      "customerId": "46b01837-0029-4541-a58a-5b65ca21a6ce",
      "customerName": "Anton Abitharshan",
      "phoneNumber": "+94768879054",
      "orderCount": 14,
      "totalSpent": 520964.74,
      "avgOrderValue": 37211.77,
      "rank": 1
    }
  ]
}
```

---

### GET /api/reports/orders/avg-delivery-time

Average, min, and max delivery time in hours for delivered orders.

**Response:**
```json
{
  "success": true,
  "message": "Average delivery time retrieved",
  "data": {
    "avgHours": 11.21,
    "minHours": 0.0,
    "maxHours": 767.60,
    "deliveredCount": 80
  }
}
```

---

## Delivery Reports

### GET /api/reports/deliveries/summary

Delivery volume, success/fail counts, and success rate.

**Response:**
```json
{
  "success": true,
  "message": "Delivery summary retrieved",
  "data": {
    "totalDeliveries": 74,
    "successCount": 15,
    "failCount": 7,
    "successRate": 20.27,
    "avgDeliveryHours": null,
    "period": { "startDate": "2026-06-01", "endDate": "2026-06-10" }
  }
}
```

---

### GET /api/reports/deliveries/by-driver

Delivery statistics grouped by driver.

**Response:**
```json
{
  "success": true,
  "message": "Delivery by driver retrieved",
  "data": [
    {
      "driverId": "6ce42c87-d81c-43f6-a511-4ea33464d6e4",
      "driverName": "Driver One",
      "totalDeliveries": 13,
      "successCount": 8,
      "failCount": 1,
      "successRate": 61.54
    }
  ]
}
```

---

### GET /api/reports/deliveries/failure-reasons

Breakdown of delivery failure reasons with percentage share.

**Response:**
```json
{
  "success": true,
  "message": "Delivery failure reasons retrieved",
  "data": [
    { "reason": "test", "count": 3, "percentage": 42.86 },
    { "reason": "Customer moved", "count": 1, "percentage": 14.29 }
  ]
}
```

---

## Commission Reports

### GET /api/reports/commissions/summary

Commission amounts grouped by status (approved, paid, processing, pending) + outstanding liability.

**Response:**
```json
{
  "success": true,
  "message": "Commission summary retrieved",
  "data": {
    "totalApproved": 64.25,
    "totalPaid": 19633.99,
    "totalProcessing": 19.50,
    "totalPending": 0,
    "outstandingLiability": 83.75,
    "period": { "startDate": "2026-06-01", "endDate": "2026-06-10" }
  }
}
```

---

### GET /api/reports/commissions/by-agent

Commission totals per agent.

**Response:**
```json
{
  "success": true,
  "message": "Commission by agent retrieved",
  "data": [
    {
      "agentId": "0c47f5df-6082-404f-ada1-689e2eb11ba9",
      "agentCode": "AGT-TEST-001",
      "agentName": "Agent Test",
      "orderCount": null,
      "totalCommission": 8.25,
      "paidAmount": 0,
      "pendingAmount": 0
    }
  ]
}
```

---

### GET /api/reports/commissions/pending-aging

Aging analysis of PENDING commissions bucketed by age.

**Response:**
```json
{
  "success": true,
  "message": "Commission pending aging retrieved",
  "data": [
    { "ageBucket": "0-7 days", "count": 0, "totalAmount": 0 },
    { "ageBucket": "7-14 days", "count": 0, "totalAmount": 0 },
    { "ageBucket": "14-30 days", "count": 0, "totalAmount": 0 },
    { "ageBucket": "30+ days", "count": 0, "totalAmount": 0 }
  ]
}
```

---

## Agent Reports

### GET /api/reports/agents/summary

Agent counts: active, inactive, new registrations in period, total.

**Response:**
```json
{
  "success": true,
  "message": "Agent summary retrieved",
  "data": {
    "totalActive": 60,
    "totalInactive": 6,
    "newRegistrations": 66,
    "totalAgents": 66,
    "period": { "startDate": "2020-01-01", "endDate": "2026-06-10" }
  }
}
```

---

### GET /api/reports/agents/by-district

Agent distribution by district. **No period filtering** (snapshot of all agents).

**Response:**
```json
{
  "success": true,
  "message": "Agent by district retrieved",
  "data": [
    { "district": "JF", "agentCount": 22, "activeCount": 22 },
    { "district": "MN", "agentCount": 9, "activeCount": 8 }
  ]
}
```

---

## Driver Reports

### GET /api/reports/drivers/summary

Driver availability breakdown. **No period filtering** (current snapshot).

**Response:**
```json
{
  "success": true,
  "message": "Driver summary retrieved",
  "data": {
    "totalDrivers": 5,
    "activeCount": 5,
    "suspendedCount": 1,
    "offlineCount": 0,
    "onTripCount": 3
  }
}
```

---

### GET /api/reports/drivers/performance

Per-driver performance metrics.

**Response:**
```json
{
  "success": true,
  "message": "Driver performance retrieved",
  "data": [
    {
      "driverId": "6ce42c87-d81c-43f6-a511-4ea33464d6e4",
      "driverName": "Driver One",
      "deliveriesAssigned": 13,
      "deliveriesCompleted": 8,
      "deliveriesFailed": 1,
      "successRate": 61.54,
      "avgDeliveryHours": null
    }
  ]
}
```

---

## Customer Reports

### GET /api/reports/customers/summary

Customer counts, new registrations, and repeat rate.

**Response:**
```json
{
  "success": true,
  "message": "Customer summary retrieved",
  "data": {
    "totalCustomers": 25,
    "newCustomersThisPeriod": 22,
    "repeatOrderCount": 11,
    "repeatRate": 44.0,
    "period": { "startDate": "2026-06-01", "endDate": "2026-06-10" }
  }
}
```

---

### GET /api/reports/customers/by-district

Customer distribution by address district. **No period filtering** (snapshot of all customers).

**Response:**
```json
{
  "success": true,
  "message": "Customer by district retrieved",
  "data": [
    { "district": "JF", "customerCount": 6 },
    { "district": "BC", "customerCount": 4 }
  ]
}
```

---

## Product Reports

### GET /api/reports/products/summary

Product counts and category count. **No period filtering** (current snapshot).

**Response:**
```json
{
  "success": true,
  "message": "Product summary retrieved",
  "data": {
    "totalProducts": 28,
    "activeCount": 24,
    "inactiveCount": 4,
    "categoryCount": 12
  }
}
```

---

### GET /api/reports/products/top-sellers

Top-selling products by revenue.

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `limit` | `int` | `10` | Number of products to return |

**Response:**
```json
{
  "success": true,
  "message": "Top sellers retrieved",
  "data": [
    {
      "productId": "9337a420-5ca9-4b30-9238-64a03da60ba6",
      "productName": "iPhone 14",
      "itemCode": "IP-001",
      "category": "new category",
      "quantitySold": 2,
      "revenue": 500000.0,
      "rank": 1
    }
  ]
}
```

---

### GET /api/reports/products/by-category

Sales summary grouped by product category.

**Response:**
```json
{
  "success": true,
  "message": "Product by category retrieved",
  "data": [
    { "category": "Electronics", "productCount": 7, "quantitySold": 241, "revenue": 31828.98 },
    { "category": "Books", "productCount": 3, "quantitySold": 45, "revenue": 5709.86 }
  ]
}
```

---

## Inventory Reports

### GET /api/reports/inventory/summary

Stock quantity, total value, low-stock and out-of-stock counts. **No period filtering** (current snapshot).

**Response:**
```json
{
  "success": true,
  "message": "Inventory summary retrieved",
  "data": {
    "totalStockQty": 1910,
    "totalStockValue": 63786160.0,
    "lowStockCount": 2,
    "outOfStockCount": 8
  }
}
```

---

### GET /api/reports/inventory/low-stock

Items where `currentStock < reorderLevel`, with the shortfall amount. **No period filtering**.

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `threshold` | `int` | `10` | Only return items where shortfall >= this value |

**Response:**
```json
{
  "success": true,
  "message": "Low stock items retrieved",
  "data": [
    {
      "productId": "760328df-14d3-4618-984f-2fa902c1057a",
      "productName": "Updated Product",
      "itemCode": "NP-002-UPDATED",
      "currentStock": 0,
      "reorderLevel": 15,
      "shortfall": 15
    }
  ]
}
```

---

## Financial Reports

### GET /api/reports/financial/profit-loss

Profit and loss statement: revenue, cost of goods sold, gross profit/margin, commissions, expenses, net profit.

**Response:**
```json
{
  "success": true,
  "message": "Profit/loss report retrieved",
  "data": {
    "totalRevenue": 545850.31,
    "totalCostPrice": 432327.0,
    "grossProfit": 113523.31,
    "grossMargin": 20.8,
    "totalCommissions": 40753.59,
    "totalExpenses": 17200.00,
    "netProfit": 55569.72,
    "period": { "startDate": "2026-06-01", "endDate": "2026-06-10" }
  }
}
```

---

### GET /api/reports/financial/commission-liability

Unpaid commissions split by APPROVED and PROCESSING status. **No period filtering** (all unpaid commissions).

**Response:**
```json
{
  "success": true,
  "message": "Commission liability retrieved",
  "data": {
    "totalUnpaid": 83.75,
    "approvedAmount": 64.25,
    "processingAmount": 19.50,
    "approvedCount": 3,
    "processingCount": 3
  }
}
```

---

## Activity Reports

### GET /api/reports/activity/audit-summary

Audit log activity breakdown by action type.

**Response:**
```json
{
  "success": true,
  "message": "Audit activity summary retrieved",
  "data": {
    "totalActions": 40,
    "actionBreakdown": [
      { "action": "LOGIN", "count": 19, "uniqueUsers": 3 },
      { "action": "UPDATE", "count": 11, "uniqueUsers": 2 },
      { "action": "CREATE", "count": 7, "uniqueUsers": 2 },
      { "action": "FORGOT_PASSWORD", "count": 1, "uniqueUsers": 0 }
    ],
    "period": { "startDate": "2026-06-01", "endDate": "2026-06-10" }
  }
}
```

---

## Data Models

### PeriodRange

```json
{
  "startDate": "2026-06-01",
  "endDate": "2026-06-10"
}
```

### Common Patterns

- **Period-aware endpoints** accept `period`, `startDate`, and `endDate` query params. Endpoints without period filtering are noted as "No period filtering".
- **Null vs zero**: `null` indicates no data available (e.g. no delivered orders in the period), `0` is a valid count/sum.
- **Percentages** are represented as `Double` (e.g. `20.8` = 20.8%).

### Endpoints Reference

| # | Endpoint | Period | Limit | Granularity |
|---|----------|--------|-------|-------------|
| 1 | `GET /sales/summary` | Yes | — | — |
| 2 | `GET /sales/trend` | Yes | — | `daily`/`weekly`/`monthly`/`yearly` |
| 3 | `GET /sales/by-source` | Yes | — | — |
| 4 | `GET /sales/by-status` | Yes | — | — |
| 5 | `GET /orders/summary` | Yes | — | — |
| 6 | `GET /orders/top-customers` | Yes | `limit` (10) | — |
| 7 | `GET /orders/avg-delivery-time` | Yes | — | — |
| 8 | `GET /deliveries/summary` | Yes | — | — |
| 9 | `GET /deliveries/by-driver` | Yes | — | — |
| 10 | `GET /deliveries/failure-reasons` | Yes | — | — |
| 11 | `GET /commissions/summary` | Yes | — | — |
| 12 | `GET /commissions/by-agent` | Yes | — | — |
| 13 | `GET /commissions/pending-aging` | Yes | — | — |
| 14 | `GET /agents/summary` | Yes | — | — |
| 15 | `GET /agents/by-district` | No | — | — |
| 16 | `GET /drivers/summary` | No | — | — |
| 17 | `GET /drivers/performance` | Yes | — | — |
| 18 | `GET /customers/summary` | Yes | — | — |
| 19 | `GET /customers/by-district` | No | — | — |
| 20 | `GET /products/summary` | No | — | — |
| 21 | `GET /products/top-sellers` | Yes | `limit` (10) | — |
| 22 | `GET /products/by-category` | Yes | — | — |
| 23 | `GET /inventory/summary` | No | — | — |
| 24 | `GET /inventory/low-stock` | No | `threshold` (10) | — |
| 25 | `GET /financial/profit-loss` | Yes | — | — |
| 26 | `GET /financial/commission-liability` | No | — | — |
| 27 | `GET /activity/audit-summary` | Yes | — | — |
