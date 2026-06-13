# Finance API Documentation

## Base URL
```
http://localhost:8085/api/finance
```

## Authentication
All finance endpoints require JWT authentication:
```
Authorization: Bearer <JWT_TOKEN>
```

### Roles
- **ADMIN** — Full access

---

## Endpoints

### 1. Get Finance Summary
**GET** `/api/finance/summary`

Retrieves a summary of revenue, commissions, expenses, and net profit for a given date range, with expense breakdown by category.

**Access:** ADMIN

**Query Parameters:**
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `start` | date (ISO) | Yes | Period start (e.g. `2026-01-01`) |
| `end` | date (ISO) | Yes | Period end (e.g. `2026-12-31`) |

**Response (200):**
```json
{
  "success": true,
  "message": "Finance summary retrieved successfully",
  "data": {
    "totalRevenue": 990890.85,
    "totalCommissions": 52133.11,
    "totalExpenses": 17200.00,
    "netProfit": 921557.74,
    "period": {
      "start": "2026-01-01",
      "end": "2026-12-31"
    },
    "expenseByCategory": [
      { "category": "FUEL", "amount": 15000.00 },
      { "category": "MAINTENANCE", "amount": 1200.00 },
      { "category": "UTILITIES", "amount": 1000.00 }
    ]
  }
}
```

**Formulas:**
- `netProfit = totalRevenue - totalCommissions - totalExpenses`
- Only `APPROVED` expenses are included in the totals
- Revenue is calculated from completed orders within the date range
