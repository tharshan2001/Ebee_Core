# Accounting API Documentation

## Base URL
```
http://localhost:8085/api/accounting
```

## Authentication
All accounting endpoints require JWT authentication:
```
Authorization: Bearer <JWT_TOKEN>
```

### Roles
| Role | Access Level |
|------|-------------|
| **ADMIN** | Full access to ledgers and periods |
| **AGENT** | Limited to own customer ledger entries |
| **SUPER_ADMIN** | Required for period archive/reopen |

---

## Ledger Service

### Overview
The ledger service maintains immutable, append-only ledger entries for:
- **Customer Ledger** — Tracks amounts owed by customers
- **Supplier Ledger** — Tracks amounts owed to suppliers
- **Agent Ledger** — Tracks agent commissions and payouts

### Sign Conventions

#### Customer Ledger
```
Balance = SUM(debit) - SUM(credit)
```
- **Debit** = Customer owes us (invoice created, manual adjustment)
- **Credit** = Customer payment received, refund, advance credit

#### Supplier Ledger
```
Balance = SUM(credit) - SUM(debit)
```
- **Credit** = We owe supplier (PO received)
- **Debit** = We paid supplier

#### Agent Ledger
```
Balance = SUM(credit) - SUM(debit)
```
- **Credit** = Commission earned
- **Debit** = Commission paid out

---

## Ledger Endpoints

### Base Path: `/api/accounting/ledger`

---

### 1. Get Customer Balance
**GET** `/api/accounting/ledger/customer/{customerId}/balance`

Retrieves the current ledger balance for a customer.

**Access:** ADMIN, AGENT

**Path Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| `customerId` | UUID | Customer identifier |

**Response (200):**
```json
{
  "success": true,
  "message": "Customer balance retrieved",
  "data": 15000.00
}
```

---

### 2. Get Supplier Balance
**GET** `/api/accounting/ledger/supplier/{supplierId}/balance`

Retrieves the current ledger balance for a supplier.

**Access:** ADMIN

**Path Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| `supplierId` | UUID | Supplier identifier |

**Response (200):**
```json
{
  "success": true,
  "message": "Supplier balance retrieved",
  "data": 5000.00
}
```

---

### 3. Get Agent Balance
**GET** `/api/accounting/ledger/agent/{agentId}/balance`

Retrieves the current ledger balance for an agent.

**Access:** ADMIN

**Path Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| `agentId` | UUID | Agent identifier |

**Response (200):**
```json
{
  "success": true,
  "message": "Agent balance retrieved",
  "data": 2500.00
}
```

---

### 4. Record Customer Invoice
**POST** `/api/accounting/ledger/customer/{customerId}/invoice`

Records a new invoice entry in the customer ledger (creates a debit).

**Access:** ADMIN

**Path Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| `customerId` | UUID | Customer identifier |

**Query Parameters:**
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `orderId` | UUID | Yes | Associated order |
| `amount` | Decimal | Yes | Invoice amount |
| `description` | String | No | Description |

**Response (200):**
```json
{
  "success": true,
  "message": "Customer invoice recorded",
  "data": {
    "id": "uuid",
    "entityId": "customer-uuid",
    "transactionType": "INVOICE",
    "debit": 10000.00,
    "credit": 0.00,
    "balance": 15000.00,
    "description": "Order #ORD-001",
    "createdAt": "2026-07-29T10:30:00"
  }
}
```

---

### 5. Record Customer Payment
**POST** `/api/accounting/ledger/customer/{customerId}/payment`

Records a payment received from a customer (creates a credit).

**Access:** ADMIN, AGENT

**Path Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| `customerId` | UUID | Customer identifier |

**Query Parameters:**
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `paymentId` | UUID | Yes | Payment identifier |
| `amount` | Decimal | Yes | Payment amount |
| `description` | String | No | Description |

**Response (200):**
```json
{
  "success": true,
  "message": "Customer payment recorded",
  "data": {
    "id": "uuid",
    "entityId": "customer-uuid",
    "transactionType": "PAYMENT",
    "debit": 0.00,
    "credit": 5000.00,
    "balance": 10000.00,
    "description": "Payment received",
    "createdAt": "2026-07-29T11:00:00"
  }
}
```

---

### 6. Record Customer Refund
**POST** `/api/accounting/ledger/customer/{customerId}/refund`

Records a refund issued to a customer.

**Access:** ADMIN

**Path Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| `customerId` | UUID | Customer identifier |

**Query Parameters:**
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `returnId` | UUID | Yes | Return identifier |
| `amount` | Decimal | Yes | Refund amount |
| `description` | String | No | Description |

---

### 7. Record Supplier Invoice
**POST** `/api/accounting/ledger/supplier/{supplierId}/invoice`

Records a purchase order received from a supplier (creates a credit = we owe them).

**Access:** ADMIN

**Path Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| `supplierId` | UUID | Supplier identifier |

**Query Parameters:**
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `purchaseOrderId` | UUID | Yes | Purchase order identifier |
| `amount` | Decimal | Yes | Invoice amount |
| `description` | String | No | Description |

---

### 8. Record Supplier Payment
**POST** `/api/accounting/ledger/supplier/{supplierId}/payment`

Records a payment made to a supplier (creates a debit = we paid them).

**Access:** ADMIN

**Path Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| `supplierId` | UUID | Supplier identifier |

**Query Parameters:**
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `supplierPaymentId` | UUID | Yes | Supplier payment identifier |
| `amount` | Decimal | Yes | Payment amount |
| `description` | String | No | Description |

---

### 9. Record Agent Commission
**POST** `/api/accounting/ledger/agent/{agentId}/commission`

Records a commission earned by an agent (creates a credit).

**Access:** ADMIN

**Path Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| `agentId` | UUID | Agent identifier |

**Query Parameters:**
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `commissionId` | UUID | Yes | Commission identifier |
| `amount` | Decimal | Yes | Commission amount |
| `description` | String | No | Description |

---

### 10. Record Agent Payout
**POST** `/api/accounting/ledger/agent/{agentId}/payout`

Records a payout made to an agent (creates a debit).

**Access:** ADMIN

**Path Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| `agentId` | UUID | Agent identifier |

**Query Parameters:**
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `payoutId` | UUID | Yes | Payout identifier |
| `amount` | Decimal | Yes | Payout amount |
| `description` | String | No | Description |

---

## Accounting Period Endpoints

### Base Path: `/api/accounting/periods`

### Overview
Accounting periods control when financial entries can be posted:
- **OPEN** — Entries can be posted
- **CLOSED** — Entries cannot be posted (can be reopened by SUPER_ADMIN)
- **ARCHIVED** — Permanent, cannot be changed

---

### 1. List All Periods
**GET** `/api/accounting/periods`

Retrieves all accounting periods.

**Access:** ADMIN

**Response (200):**
```json
{
  "success": true,
  "message": "Periods retrieved",
  "data": [
    {
      "id": "uuid",
      "month": 7,
      "year": 2026,
      "status": "OPEN",
      "openedAt": "2026-07-01T00:00:00",
      "closedAt": null,
      "reason": null
    }
  ]
}
```

---

### 2. Get Periods by Year
**GET** `/api/accounting/periods/year/{year}`

Retrieves all accounting periods for a specific year.

**Access:** ADMIN

**Path Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| `year` | Integer | Year (e.g., 2026) |

---

### 3. Get Specific Period
**GET** `/api/accounting/periods/month/{month}/year/{year}`

Retrieves a specific accounting period.

**Access:** ADMIN

**Path Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| `month` | Integer | Month (1-12) |
| `year` | Integer | Year |

---

### 4. Get Open Periods
**GET** `/api/accounting/periods/open`

Retrieves all periods with OPEN status.

**Access:** ADMIN

---

### 5. Open Period
**POST** `/api/accounting/periods/open`

Opens an accounting period for a specific month/year.

**Access:** ADMIN

**Query Parameters:**
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `year` | Integer | Yes | Year |
| `month` | Integer | Yes | Month (1-12) |

**Response (200):**
```json
{
  "success": true,
  "message": "Period opened",
  "data": {
    "id": "uuid",
    "month": 7,
    "year": 2026,
    "status": "OPEN",
    "openedAt": "2026-07-29T10:00:00",
    "closedAt": null,
    "reason": null
  }
}
```

---

### 6. Close Period
**POST** `/api/accounting/periods/close`

Closes an accounting period. Once closed, entries cannot be posted without reopening.

**Access:** ADMIN

**Request Body:**
```json
{
  "periodId": "uuid",
  "reason": "End of month closure"
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "Period closed",
  "data": {
    "id": "uuid",
    "month": 6,
    "year": 2026,
    "status": "CLOSED",
    "openedAt": "2026-06-01T00:00:00",
    "closedAt": "2026-07-29T10:00:00",
    "reason": "End of month closure"
  }
}
```

---

### 7. Reopen Period
**POST** `/api/accounting/periods/reopen`

Reopens a CLOSED accounting period. ARCHIVED periods cannot be reopened.

**Access:** SUPER_ADMIN

**Request Body:**
```json
{
  "periodId": "uuid",
  "reason": "Correcting June entries"
}
```

---

### 8. Archive Period
**POST** `/api/accounting/periods/archive`

Permanently archives a period. Archived periods cannot be modified or reopened.

**Access:** SUPER_ADMIN

**Request Body:**
```json
{
  "periodId": "uuid",
  "reason": "Year-end permanent closure"
}
```

---

## Period Status Transitions

```
OPEN ──────► CLOSED ──────► ARCHIVED
  ▲            │                  │
  │            │                  │
  └────────────┴──────────────────┘
            (can reopen)
```

### Rules:
1. **OPEN → CLOSED**: Allowed by ADMIN
2. **CLOSED → OPEN**: Allowed by SUPER_ADMIN only
3. **CLOSED → ARCHIVED**: Allowed by SUPER_ADMIN only
4. **ARCHIVED**: Permanent, no transitions allowed

---

## Audit Trail

All period status changes are recorded in `accounting_period_audit`:

| Field | Description |
|-------|-------------|
| `period_id` | Period identifier |
| `action` | CLOSE, REOPEN, ARCHIVE |
| `old_status` | Previous status |
| `new_status` | New status |
| `reason` | Reason for change |
| `changed_by` | User who made change |
| `changed_at` | Timestamp |

---

## Accounting Period Transactions

### Overview
Every ledger entry is automatically recorded in `accounting_period_transactions` when posted through the ledger service. This provides:
- **Granular transaction tracking** per accounting period
- **Audit trail** linking ledger entries to specific periods
- **Automatic period assignment** based on current date

### How It Works
When a ledger entry is created (INVOICE, PAYMENT, REFUND, ADVANCE):
1. The entry is saved to the appropriate ledger table (customer/supplier/agent)
2. The system checks for an **OPEN** period matching the current month/year
3. If an open period exists, a corresponding entry is created in `accounting_period_transactions`

### Transaction Table Schema
```sql
accounting_period_transactions (
    id              UUID PRIMARY KEY,
    period_id       UUID REFERENCES accounting_periods(id),
    fiscal_year     INTEGER NOT NULL,
    month           INTEGER NOT NULL,
    year            INTEGER NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,  -- INVOICE, PAYMENT, REFUND, ADVANCE
    debit           DECIMAL(15,2) DEFAULT 0,
    credit          DECIMAL(15,2) DEFAULT 0,
    reference_id    UUID,                   -- order/payment/return ID
    reference_type  VARCHAR(50),             -- ORDER, PAYMENT, RETURN, ADVANCE
    description     VARCHAR(500),
    recorded_by     UUID NOT NULL,          -- user who recorded
    recorded_at     TIMESTAMP NOT NULL
)
```

### Auto-Sync Triggers
| Event | Ledger Entry | Period Transaction |
|-------|--------------|-------------------|
| Order created | Debit = order total | INVOICE (debit) |
| Payment recorded | Credit = payment amount | PAYMENT (credit) |
| Order returned | Credit = paid amount | REFUND (debit) |
| Advance credited | Credit = advance amount | ADVANCE (credit) |

### Behavior When No Open Period
If no open period exists for the current month/year:
- The ledger entry is still created (ledger is always authoritative)
- A warning is logged: `"No open period found for {}/{}, transaction not recorded"`
- No period transaction entry is created
- Admin must open a period before entries can be tracked

### Benefits
1. **Period-based reporting** - Aggregate transactions by period
2. **Audit compliance** - Every entry linked to who recorded it and when
3. **Reconciliation** - Can reconcile ledger totals against period transaction totals
4. **Automatic** - No manual intervention required for customer ledger entries

---

## Customer Ledger API

### Base Path: `/api/customers/{customerId}/ledger`

These endpoints provide detailed access to customer ledger entries with full filtering and pagination support.

---

### 1. Get Customer Ledger Entries
**GET** `/api/customers/{customerId}/ledger`

Retrieves paginated customer ledger entries with optional filters.

**Access:** ADMIN

**Path Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| `customerId` | UUID | Customer identifier |

**Query Parameters:**
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `page` | int | No | Page number (default: 0) |
| `size` | int | No | Page size (default: 20) |
| `entryType` | String | No | Filter by transaction type (e.g., INVOICE, PAYMENT, REFUND) |
| `startDate` | ISO DateTime | No | Filter entries after this date |
| `endDate` | ISO DateTime | No | Filter entries before this date |

**Response (200):**
```json
{
  "success": true,
  "message": "Customer ledger retrieved successfully",
  "data": {
    "content": [
      {
        "id": "uuid",
        "entityId": "customer-uuid",
        "transactionType": "INVOICE",
        "debit": 10000.00,
        "credit": 0.00,
        "description": "Order JF-A-20260101-001",
        "orderId": "order-uuid",
        "paymentId": null,
        "returnId": null,
        "createdAt": "2026-07-29T10:30:00"
      },
      {
        "id": "uuid",
        "entityId": "customer-uuid",
        "transactionType": "PAYMENT",
        "debit": 0.00,
        "credit": 5000.00,
        "description": "Payment for Order JF-A-20260101-001 via CASH",
        "orderId": "order-uuid",
        "paymentId": "payment-uuid",
        "returnId": null,
        "createdAt": "2026-07-29T11:00:00"
      }
    ],
    "totalElements": 2,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

### 2. Get Customer Ledger Balance
**GET** `/api/customers/{customerId}/ledger/outstanding`

Retrieves the current ledger balance (sum of debits - credits) for a customer.

**Access:** ADMIN

**Path Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| `customerId` | UUID | Customer identifier |

**Response (200):**
```json
{
  "success": true,
  "message": "Customer ledger balance retrieved",
  "data": 5000.00
}
```

---

## Auto-Sync Points

The customer ledger is automatically updated when the following events occur:

| Event | Ledger Entry | Entry Type |
|-------|--------------|------------|
| Order created | Debit = order total amount | INVOICE |
| Payment recorded | Credit = payment amount | PAYMENT |
| Order returned | Credit = paid amount | REFUND |
| Advance credited | Credit = advance amount | ADVANCE |

---

## Internal Service Methods

The `LedgerService` contains additional internal methods not exposed as REST endpoints:

| Method | Purpose | Called By |
|--------|---------|-----------|
| `recordSupplierExpense()` | Records supplier expense (debit) | Internal only |
| `getCustomerBalance()` | Calculates customer balance | Internal + REST |
| `getSupplierBalance()` | Calculates supplier balance | Internal + REST |
| `getAgentBalance()` | Calculates agent balance | Internal + REST |

These internal methods are available for programmatic use but do not have corresponding REST API endpoints.

---

## Migration Notes

### Required Migrations
| Version | Description |
|---------|-------------|
| V101 | Creates fiscal_years, accounting_periods, accounting_period_audit tables |
| V102 | Creates customer_ledger, supplier_ledger, agent_ledger tables |
| V106 | Adds CHECK constraint on order_items (product_id XOR combo_id) |
| V107 | Adds book_no column to customers table |
| V108 | Fixes fiscal_years schema: renames `year`→`fiscal_year`, `is_closed`→`is_active`, drops `name`, adds `updated_at` |
| V109 | Creates accounting_period_transactions table for automatic period transaction tracking |

### Ledger Initialization
On migration V102:
- All existing customer outstanding balances are cached
- All existing supplier outstanding balances are cached
- All agent balance caches are initialized to 0

---

## Error Responses

### 400 Bad Request
```json
{
  "success": false,
  "message": "Period is already closed"
}
```

Common 400 errors:
- `"No open period found for {}/{}, transaction not recorded"` - No OPEN period exists for current month/year
- `"Period is not open (status={})"` - Attempted to post to a non-OPEN period
- `"Period not found"` - Requested period doesn't exist
- `"Can only close OPEN periods"` - Period must be OPEN to close
- `"Can only reopen CLOSED periods"` - Period must be CLOSED to reopen

### 403 Forbidden
```json
{
  "success": false,
  "message": "Access denied"
}
```

### 404 Not Found
```json
{
  "success": false,
  "message": "Period not found"
}
```

### 500 Internal Server Error
```json
{
  "success": false,
  "message": "An unexpected error occurred"
}
```

---

## Period Transaction Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                     LEDGER ENTRY CREATED                            │
│            (Order, Payment, Return, Advance)                        │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     CHECK CURRENT MONTH/YEAR                        │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    ▼                           ▼
         ┌─────────────────┐         ┌─────────────────┐
         │  OPEN PERIOD    │         │ NO OPEN PERIOD  │
         │    EXISTS       │         │    FOR MONTH    │
         └─────────────────┘         └─────────────────┘
                    │                           │
                    ▼                           ▼
┌─────────────────────────────┐    ┌─────────────────────────────┐
│  1. Save Ledger Entry       │    │  1. Save Ledger Entry       │
│  2. Find Period by          │    │  2. LOG WARNING             │
│     Month + Year            │    │     "No open period..."     │
│  3. Create Period           │    │  3. NO Period Transaction   │
│     Transaction             │    │     Created                 │
│  4. Return Ledger Entry     │    │  4. Return Ledger Entry     │
└─────────────────────────────┘    └─────────────────────────────┘
```
