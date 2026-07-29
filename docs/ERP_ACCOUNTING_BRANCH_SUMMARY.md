# ERP Accounting Hardening - Branch Summary

## What Was Done

### 1. Customer Ledger Auto-Sync
- **Book No Field**: Added `book_no` column to `customers` table (V107)
- **Auto-Recording**: Customer ledger entries created automatically on:
  - Order creation → INVOICE (debit)
  - Payment recording → PAYMENT (credit)
  - Order return → REFUND (credit)
  - Advance credited → ADVANCE (credit)

### 2. Accounting Period Transactions
- **New Table**: `accounting_period_transactions` (V109) - tracks all ledger entries against periods
- **Auto-Recording**: Every ledger entry automatically recorded to current open period
- **Period Enforcement**: Entries only recorded if matching OPEN period exists
- **Audit Fields**: `recorded_by`, `recorded_at` on all transactions

### 3. Fiscal Year Schema Fix
- **Migration V108**: Fixed `fiscal_years` table schema
  - Renamed `year` → `fiscal_year`
  - Renamed `is_closed` → `is_active`
  - Added `updated_at` column

### 4. Unified Reports API
- **New Endpoint**: `GET /api/reports/summary` - combined sales + finance + dashboard metrics
- **New Endpoint**: `GET /api/reports/trial-balance` - all ledger entries by type with totals
- **New Endpoint**: `GET /api/reports/aging` - customer outstanding aging buckets (current/30/60/90/90+)
- **Deprecated**: Old `/api/finance/summary`, `/api/sales/summary`, `/api/sales/dashboard`

### 5. Database Migrations
| Version | Description |
|---------|-------------|
| V101 | Creates fiscal_years, accounting_periods, accounting_period_audit tables |
| V102 | Creates customer_ledger, supplier_ledger, agent_ledger tables |
| V106 | Adds CHECK constraint on order_items (product_id XOR combo_id) |
| V107 | Adds book_no column to customers table |
| V108 | Fixes fiscal_years schema (year→fiscal_year, is_closed→is_active) |
| V109 | Creates accounting_period_transactions table |

---

## What Needs to Be Added for Full Control

### 1. Supplier & Agent Ledger Auto-Sync
**Current State**: Only customer ledger auto-syncs to accounting periods
**Needed**:
- `recordSupplierInvoice` → should call `recordPeriodTransaction()`
- `recordSupplierPayment` → should call `recordPeriodTransaction()`
- `recordAgentCommission` → should call `recordPeriodTransaction()`
- `recordAgentPayout` → should call `recordPeriodTransaction()`

### 2. Accounting Period Management UI
**Current State**: No admin UI for period management
**Needed**:
- Open/Close/Reopen/Archive periods via API (endpoints exist)
- Admin dashboard widget showing current period status
- Warning when no open period exists for current month

### 3. Trial Balance Report
**Current State**: Basic endpoint returns all ledger entries
**Needed**:
- Period selector (filter by specific month/year)
- Group by entity type (customer/supplier/agent)
- Export to CSV/PDF
- Totals validation (debit = credit)

### 4. Aging Report Enhancements
**Current State**: Basic aging buckets by customer
**Needed**:
- Filter by date range
- Filter by agent (show only their customers)
- Export to CSV/PDF
- Drill-down to specific customer orders

### 5. Daily Cash Flow Report
**Current State**: Not implemented
**Needed**:
- `GET /api/reports/cash-flow?month=7&year=2026`
- Daily breakdown: payments received, refunds issued, expenses paid
- Running balance per day

### 6. Commission Report
**Current State**: Basic commission calculation
**Needed**:
- `GET /api/reports/commissions?period=monthly`
- By agent breakdown
- By status (pending/approved/paid)
- Export to CSV/PDF

### 7. Supplier Liability Report
**Current State**: Not implemented
**Needed**:
- `GET /api/reports/supplier-liabilities`
- All outstanding amounts to suppliers
- By supplier breakdown
- Aging of PO receipts

### 8. Dashboard Widgets
**Current State**: Basic summary metrics
**Needed**:
- Current accounting period status (is it open?)
- Outstanding vs collected this month
- Low stock alerts
- Commission liability (pending + approved, not yet paid)

### 9. Audit Log Integration
**Current State**: Basic audit logging exists
**Needed**:
- UI to view audit logs
- Filter by entity type, action, user, date range
- Export to CSV

### 10. Role-Based Access for Reports
**Current State**: Most reports require ADMIN only
**Needed**:
- AGENT: Can view own sales stats, own commission status
- ADMIN: Full access to all reports
- SUPER_ADMIN: Can view/edit accounting periods

---

## API Endpoints Summary

### Reports API (`/api/reports`)
| Endpoint | Method | Access | Description |
|----------|--------|--------|-------------|
| `/summary` | GET | ADMIN | Combined sales + finance + dashboard |
| `/trial-balance` | GET | ADMIN | All ledger entries with totals |
| `/aging` | GET | ADMIN | Customer outstanding aging buckets |

### Accounting API (`/api/accounting`)
| Endpoint | Method | Access | Description |
|----------|--------|--------|-------------|
| `/periods` | GET | ADMIN | List all periods |
| `/periods/open` | GET/POST | ADMIN | Get/Create open periods |
| `/periods/close` | POST | ADMIN | Close a period |
| `/periods/reopen` | POST | SUPER_ADMIN | Reopen closed period |
| `/periods/archive` | POST | SUPER_ADMIN | Archive period |
| `/ledger/customer/{id}/balance` | GET | ADMIN | Customer balance |
| `/ledger/customer/{id}/invoice` | POST | ADMIN | Manual invoice entry |
| `/ledger/customer/{id}/payment` | POST | ADMIN/AGENT | Manual payment entry |

### Customer Ledger API (`/api/customers`)
| Endpoint | Method | Access | Description |
|----------|--------|--------|-------------|
| `/{id}/ledger` | GET | ADMIN | Customer ledger entries |
| `/{id}/ledger/outstanding` | GET | ADMIN | Customer outstanding balance |
| `/book-no/{bookNo}` | GET | Public | Lookup by book number |

---

## Database Schema Summary

### Core Accounting Tables
- `fiscal_years` - Fiscal year definitions
- `accounting_periods` - Monthly periods with status (OPEN/CLOSED/ARCHIVED)
- `accounting_period_audit` - Period status change history
- `accounting_period_transactions` - Automatic transaction tracking

### Ledger Tables
- `customer_ledger` - Customer accounts receivable
- `supplier_ledger` - Supplier accounts payable
- `agent_ledger` - Agent commission tracking

### Balance Caches
- `customer_balance_cache` - Cached customer balances
- `supplier_balance_cache` - Cached supplier balances
- `agent_balance_cache` - Cached agent balances

---

## Configuration

### Environment Variables Required
```
DB_URL=jdbc:postgresql://localhost:5432/ebee_db
DB_USERNAME=ebee
DB_PASSWORD=<password>
JWT_SECRET=<256-bit-secret>
```

### Security
- Public endpoints: `/api/auth/**`, `/api/customers/phone/**`, `/api/customers/book-no/**`, `/api/products/**`, `/api/categories/**`, `/api/combos/{id}`
- ADMIN endpoints: All accounting and report endpoints
- SUPER_ADMIN: Period archive/reopen only
