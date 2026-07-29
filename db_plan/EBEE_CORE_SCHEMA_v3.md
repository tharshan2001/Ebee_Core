# Ebee Core - POS/ERP/Delivery Schema (v3 - Final Production)

**PostgreSQL with ENUM types**

**Last Updated:** July 29, 2026

---

## Comprehensive Fixes Applied

- Inventory locking (SELECT FOR UPDATE)
- Order items CHECK constraint (PostgreSQL enforced)
- Ledger sign convention corrected
- Fiscal years & accounting periods with period control
- Stock transfers (inter-warehouse)
- Audit partitioning guidance
- Partial indexes for soft delete

---

## Table of Contents

1. [ENUM Types](#enum-types)
2. [Shared/Lookup Tables](#shared--lookup-tables)
3. [Users & Authentication](#users--authentication)
4. [Products & Inventory](#products--inventory)
5. [Stock Transfers](#stock-transfers)
6. [Combo/Bundles](#combo-bundles)
7. [Customers](#customers)
8. [Orders](#orders)
9. [Deliveries](#deliveries)
10. [Payments](#payments)
11. [Returns](#returns)
12. [Customer Ledger](#customer-ledger)
13. [Supplier Ledger](#supplier-ledger)
14. [Invoices](#invoices)
15. [Credit Notes](#credit-notes)
16. [Commissions](#commissions)
17. [Purchase Orders / Suppliers](#purchase-orders--suppliers)
18. [Leads](#leads)
19. [Expenses](#expenses)
20. [Accounting / GL](#accounting--gl)
21. [Notifications & Messaging](#notifications--messaging)
22. [Unified Audit](#unified-audit)
23. [System Tables](#system-tables)
24. [Relationships](#relationships)
25. [Indexes](#indexes)
26. [Migrations Summary](#migrations-summary)

---

## ENUM Types

```dbml
Enum user_role {
  SUPER_ADMIN
  ADMIN
  AGENT
  DRIVER
  CUSTOMER
}

Enum user_status {
  ACTIVE
  INACTIVE
  SUSPENDED
}

Enum order_status {
  PENDING
  CONFIRMED
  PROCESSING
  SHIPPED
  DELIVERED
  CANCELLED
  RETURNED
}

Enum order_source {
  AGENT
  DIRECT
}

Enum discount_type {
  PERCENTAGE
  FIXED
}

Enum delivery_status {
  PENDING
  ASSIGNED
  PICKED_UP
  IN_TRANSIT
  DELIVERED
  FAILED
}

Enum payment_status {
  PENDING
  PROCESSING
  SUCCESS
  FAILED
  CANCELLED
  REFUNDED
  PARTIALLY_REFUNDED
  VOIDED
}

Enum payment_method_type {
  CASH
  BANK_TRANSFER
  CARD
  CREDIT
  CHEQUE
  ONLINE
}

Enum payment_transaction_type {
  AUTHORIZATION
  CAPTURE
  PAYMENT
  VOID
  REFUND
  PARTIAL_REFUND
  FAILED
}

Enum payment_reference_type {
  ORDER
  ADVANCE
  WALLET
  REFUND
}

Enum return_status {
  PENDING
  INSPECTED
  APPROVED
  REJECTED
  COMPLETED
  REFUNDED
}

Enum item_condition {
  NEW
  OPENED
  DAMAGED
  DEFECTIVE
}

Enum commission_status {
  PENDING
  CALCULATED
  APPROVED
  PROCESSING
  PAID
  REJECTED
  ERROR
  CANCELLED
  VOIDED
}

Enum commission_type {
  DEFAULT
  PERFORMANCE
}

Enum expense_status {
  PENDING
  APPROVED
  REJECTED
  VOIDED
}

Enum expense_category {
  FUEL
  MAINTENANCE
  OTHER
}

Enum driver_availability {
  AVAILABLE
  ON_DELIVERY
  OFFLINE
}

Enum purchase_order_status {
  DRAFT
  SENT
  RECEIVED
  PARTIALLY_RECEIVED
  CANCELLED
}

Enum purchase_payment_status {
  UNPAID
  PARTIALLY_PAID
  PAID
  CANCELLED
  VOIDED
}

Enum purchase_receipt_status {
  DRAFT
  CONFIRMED
  CANCELLED
}

Enum inventory_movement_type {
  PURCHASE
  SALE
  RETURN
  DAMAGE
  ADJUSTMENT
  TRANSFER
  COMBO_SALE
}

Enum inventory_direction {
  IN
  OUT
}

Enum inventory_reservation_status {
  RESERVED
  CONFIRMED
  CANCELLED
  EXPIRED
}

Enum ledger_transaction_type {
  INVOICE
  ADVANCE
  PAYMENT
  REFUND
  ADJUSTMENT
  CREDIT_NOTE
  RETURN_REFUND
  DEBIT
  CREDIT
}

Enum combo_rule_type {
  FREE_PRODUCT
  DISCOUNT
}

Enum reminder_status {
  PENDING
  COMPLETED
  CANCELLED
}

Enum reminder_priority {
  LOW
  MEDIUM
  HIGH
}

Enum alert_type {
  ORDER
  COMMISSION
  DELIVERY
  SYSTEM
}

Enum message_target_type {
  ALL
  AGENTS
  CUSTOMERS
  DRIVERS
}

Enum payout_status {
  PENDING
  PROCESSING
  PAID
  CANCELLED
}

Enum agent_document_type {
  NIC
  BANK_PASSBOOK
  OTHER
}

Enum audit_category {
  ENTITY
  FINANCIAL
  SYSTEM
}

Enum delivery_proof_type {
  PHOTO
  SIGNATURE
  LOCATION
  ID
}

Enum invoice_status {
  DRAFT
  ISSUED
  SENT
  PAID
  PARTIALLY_PAID
  VOIDED
}

Enum credit_note_status {
  DRAFT
  ISSUED
  APPLIED
  VOIDED
}

Enum chart_of_accounts_category {
  ASSET
  LIABILITY
  EQUITY
  REVENUE
  EXPENSE
}

Enum journal_status {
  DRAFT
  POSTED
  CANCELLED
}

Enum account_type {
  BANK
  CASH
  REVENUE
  EXPENSE
  RECEIVABLE
  PAYABLE
}

Enum period_status {
  OPEN
  CLOSED
  ARCHIVED
}

Enum stock_transfer_status {
  PENDING
  IN_TRANSIT
  COMPLETED
  CANCELLED
}
```

---

## SHARED / LOOKUP TABLES

```dbml
Table addresses {
  id uuid [pk, default: `gen_random_uuid()`]
  street varchar(255) [not null]
  address_line_2 varchar(255)
  city varchar(100) [not null]
  district varchar(100) [not null]
  postal_code varchar(20)
  country varchar(100) [not null, default: 'Sri Lanka']
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  deleted_at timestamp
  
  Index: idx_addresses_district on addresses(district) WHERE deleted_at IS NULL
}

Table tags {
  id uuid [pk, default: `gen_random_uuid()`]
  name varchar(100) [unique, not null]
  slug varchar(100) [unique]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  deleted_at timestamp
}

Table categories {
  id uuid [pk, default: `gen_random_uuid()`]
  name varchar(100) [unique, not null]
  is_active boolean [not null, default: true]
  slug varchar(255) [unique]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  deleted_at timestamp
  
  Index: idx_categories_active on categories(name) WHERE deleted_at IS NULL AND is_active = true
}

Table payment_methods {
  id uuid [pk, default: `gen_random_uuid()`]
  name varchar(50) [unique, not null]
  provider varchar(50)
  type payment_method_type [not null]
  is_online boolean [not null, default: false]
  is_active boolean [not null, default: true]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  deleted_at timestamp
  
  Index: idx_payment_methods_active on payment_methods(name) WHERE deleted_at IS NULL AND is_active = true
}

Table warehouses {
  id uuid [pk, default: `gen_random_uuid()`]
  name varchar(100) [unique, not null]
  location varchar(255)
  is_active boolean [not null, default: true]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  deleted_at timestamp
  
  Index: idx_warehouses_active on warehouses(name) WHERE deleted_at IS NULL AND is_active = true
}

Table tax_rates {
  id uuid [pk, default: `gen_random_uuid()`]
  name varchar(50) [unique, not null]
  percentage decimal(5,4) [not null, check: 'percentage >= 0']
  is_default boolean [not null, default: false]
  effective_from timestamp [not null]
  effective_to timestamp
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
}
```

---

## USERS & AUTHENTICATION

```dbml
Table users {
  id uuid [pk, default: `gen_random_uuid()`]
  email varchar(255) [unique, not null]
  password varchar(255) [not null]
  first_name varchar(100) [not null]
  last_name varchar(100) [not null]
  phone_number varchar(20)
  role user_role [not null]
  status user_status [not null, default: 'ACTIVE']
  is_active boolean [not null, default: true]
  must_change_password boolean [not null, default: true]
  password_changed_at timestamp
  profile_picture_url varchar(1024)
  profile_picture_name varchar(255)
  profile_picture_type varchar(100)
  login_alerts_enabled boolean [not null, default: true]
  phone_verified boolean [not null, default: false]
  refresh_token_hash varchar(64)
  setup_token_hash varchar(64)
  last_login_at timestamp
  failed_login_attempts integer [default: 0, check: 'failed_login_attempts >= 0']
  locked_until timestamp
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  deleted_at timestamp
  
  Index: idx_users_email on users(email)
  Index: idx_users_role on users(role)
  Index: idx_users_status on users(status)
  Index: idx_users_deleted on users(deleted_at)
  Index: idx_users_active_email on users(email) WHERE deleted_at IS NULL AND is_active = true
  Index: idx_users_active_role on users(role) WHERE deleted_at IS NULL AND is_active = true
}

Table user_sessions {
  id uuid [pk, default: `gen_random_uuid()`]
  user_id uuid [not null]
  refresh_token_hash varchar(64) [not null]
  device_name varchar(100)
  device_id varchar(255)
  ip_address varchar(45)
  last_used_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  expires_at timestamp
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_user_sessions_user on user_sessions(user_id)
  Index: idx_user_sessions_token on user_sessions(refresh_token_hash)
}

Table ip_whitelist {
  id uuid [pk, default: `gen_random_uuid()`]
  user_id uuid [not null]
  ip_address varchar(45) [not null]
  created_by uuid
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'UNIQUE (user_id, ip_address)'
}

Table agents {
  id uuid [pk, default: `gen_random_uuid()`]
  user_id uuid [unique, not null]
  agent_code varchar(50) [unique, not null]
  address_id uuid [not null]
  commission_rate decimal(12,2) [not null, default: 0.00, check: 'commission_rate >= 0']
  total_sales decimal(12,2) [default: 0.00, check: 'total_sales >= 0']
  total_commission decimal(12,2) [default: 0.00, check: 'total_commission >= 0']
  is_active boolean [not null, default: true]
  commission_type commission_type [not null, default: 'DEFAULT']
  nic_number varchar(20)
  bank_account_number varchar(50)
  bank_name varchar(100)
  bank_branch varchar(100)
  version bigint [not null, default: 0]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  deleted_at timestamp
  
  Index: idx_agents_user_id on agents(user_id)
  Index: idx_agents_code on agents(agent_code)
  Index: idx_agents_active on agents(is_active)
  Index: idx_agents_deleted on agents(deleted_at)
  Index: idx_agents_active_code on agents(agent_code) WHERE deleted_at IS NULL AND is_active = true
}

Table agent_documents {
  id uuid [pk, default: `gen_random_uuid()`]
  agent_id uuid [not null]
  document_type agent_document_type [not null]
  file_url varchar(500) [not null]
  file_name varchar(255)
  file_type varchar(100)
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'UNIQUE (agent_id, document_type)'
}

Table drivers {
  id uuid [pk, default: `gen_random_uuid()`]
  user_id uuid [unique, not null]
  license_number varchar(50) [unique]
  availability driver_availability [not null, default: 'AVAILABLE']
  is_active boolean [default: true]
  fcm_token varchar(500)
  version bigint [not null, default: 0]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  deleted_at timestamp
  
  Index: idx_drivers_availability on drivers(availability)
  Index: idx_drivers_available on drivers(user_id) WHERE deleted_at IS NULL AND is_active = true AND availability = 'AVAILABLE'
}

Table agent_applications {
  id uuid [pk, default: `gen_random_uuid()`]
  first_name varchar(100) [not null]
  last_name varchar(100) [not null]
  email varchar(255) [not null]
  phone_number varchar(20) [not null]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
}
```

---

## PRODUCTS & INVENTORY

```dbml
Table products {
  id uuid [pk, default: `gen_random_uuid()`]
  name varchar(255) [not null]
  item_code varchar(100) [unique, not null]
  description text
  category_id uuid [not null]
  price decimal(12,2) [not null, check: 'price >= 0']
  cost_price decimal(12,2) [not null, check: 'cost_price >= 0']
  is_active boolean [not null, default: true]
  discount_type discount_type
  discount_value decimal(12,2) [check: 'discount_value IS NULL OR discount_value >= 0']
  max_discount_type discount_type
  max_discount_value decimal(12,2) [check: 'max_discount_value IS NULL OR max_discount_value >= 0']
  brand varchar(100)
  specifications jsonb [default: `'[]'::jsonb`]
  featured boolean [not null, default: false]
  slug varchar(255) [unique]
  tax_rate_id uuid
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  deleted_at timestamp
  
  Index: idx_products_category on products(category_id)
  Index: idx_products_active on products(is_active)
  Index: idx_products_sku on products(item_code)
  Index: idx_products_deleted on products(deleted_at)
  Index: idx_products_active_category on products(category_id) WHERE deleted_at IS NULL AND is_active = true
  Index: idx_products_active_sku on products(item_code) WHERE deleted_at IS NULL AND is_active = true
}

Table product_price_history {
  id uuid [pk, default: `gen_random_uuid()`]
  product_id uuid [not null]
  old_price decimal(12,2) [check: 'old_price >= 0']
  new_price decimal(12,2) [not null, check: 'new_price >= 0']
  old_cost_price decimal(12,2) [check: 'old_cost_price >= 0']
  new_cost_price decimal(12,2) [not null, check: 'new_cost_price >= 0']
  changed_fields jsonb [not null]
  changed_by uuid
  reason varchar(255)
  effective_from timestamp [not null, default: `CURRENT_TIMESTAMP`]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_product_price_history_product on product_price_history(product_id)
  Index: idx_product_price_history_date on product_price_history(effective_from)
}

Table product_tags {
  product_id uuid [not null]
  tag_id uuid [not null]
  
  Note: 'PRIMARY KEY (product_id, tag_id)'
}

Table product_images {
  id uuid [pk, default: `gen_random_uuid()`]
  product_id uuid [not null]
  image_url varchar(500) [not null]
  image_name varchar(255)
  image_type varchar(100)
  sort_order integer [default: 0]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  deleted_at timestamp
  
  Note: 'UNIQUE (product_id, sort_order)'
}

Table inventory {
  id uuid [pk, default: `gen_random_uuid()`]
  product_id uuid [unique, not null]
  warehouse_id uuid [not null]
  quantity integer [not null, default: 0, check: 'quantity >= 0']
  reserved_quantity integer [not null, default: 0, check: 'reserved_quantity >= 0']
  reorder_level integer [check: 'reorder_level IS NULL OR reorder_level >= 0']
  average_cost decimal(12,2) [not null, default: 0, check: 'average_cost >= 0']
  version bigint [not null, default: 0]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'CRITICAL: All stock-modifying methods MUST use SELECT FOR UPDATE'
  Note: 'InventoryRepository: findByProductIdWithLock(productId) with @Lock(PESSIMISTIC_WRITE)'
  Note: 'This prevents race conditions where two orders reserve last item simultaneously'
  
  Index: idx_inventory_product on inventory(product_id)
  Index: idx_inventory_warehouse on inventory(warehouse_id)
  Index: idx_inventory_composite on inventory(product_id, warehouse_id)
  Index: idx_inventory_qty on inventory(quantity)
}

Table inventory_layers {
  id uuid [pk, default: `gen_random_uuid()`]
  inventory_id uuid [not null]
  quantity integer [not null, check: 'quantity > 0']
  remaining_quantity integer [not null, check: 'remaining_quantity >= 0 AND remaining_quantity <= quantity']
  unit_cost decimal(12,2) [not null, check: 'unit_cost >= 0']
  received_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'FIFO layers - consume oldest first (ORDER BY received_at ASC)'
  
  Index: idx_inventory_layers_inventory on inventory_layers(inventory_id)
  Index: idx_inventory_layers_remaining on inventory_layers(inventory_id, remaining_quantity) WHERE remaining_quantity > 0
}

Table inventory_layer_transactions {
  id uuid [pk, default: `gen_random_uuid()`]
  inventory_layer_id uuid [not null]
  reference_type varchar(50) [not null]
  reference_id uuid [not null]
  quantity_used integer [not null, check: 'quantity_used > 0']
  unit_cost decimal(12,2) [not null, check: 'unit_cost >= 0']
  created_by uuid
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'Polymorphic reference - validate reference_type + reference_id in service layer'
  
  Index: idx_inventory_layer_txn_layer on inventory_layer_transactions(inventory_layer_id)
  Index: idx_inventory_layer_txn_reference on inventory_layer_transactions(reference_type, reference_id)
}

Table inventory_transactions {
  id uuid [pk, default: `gen_random_uuid()`]
  inventory_id uuid [not null]
  warehouse_id uuid [not null]
  reference_type varchar(50) [not null]
  reference_id uuid
  movement_type inventory_movement_type [not null]
  direction inventory_direction [not null]
  quantity integer [not null, check: 'quantity > 0']
  balance_before integer [not null, default: 0]
  balance_after integer [not null, default: 0]
  unit_cost decimal(12,2) [check: 'unit_cost IS NULL OR unit_cost >= 0']
  notes text
  created_by uuid
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'Polymorphic reference - validate reference_type + reference_id in service layer'
  
  Index: idx_inv_txn_inventory on inventory_transactions(inventory_id)
  Index: idx_inv_txn_warehouse on inventory_transactions(warehouse_id)
  Index: idx_inv_txn_type on inventory_transactions(movement_type)
  Index: idx_inv_txn_reference on inventory_transactions(reference_type, reference_id)
}

Table inventory_reservations {
  id uuid [pk, default: `gen_random_uuid()`]
  inventory_id uuid [not null]
  order_id uuid [not null]
  quantity integer [not null, check: 'quantity > 0']
  status inventory_reservation_status [not null, default: 'RESERVED']
  expires_at timestamp
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_inventory_reservations_inventory on inventory_reservations(inventory_id)
  Index: idx_inventory_reservations_order on inventory_reservations(order_id)
  Index: idx_inventory_reservations_status on inventory_reservations(status)
  Index: idx_inventory_reservations_expiry on inventory_reservations(expires_at, status) WHERE status IN ('RESERVED', 'CONFIRMED')
}
```

---

## STOCK TRANSFERS

*Inter-warehouse movements*

```dbml
Table stock_transfers {
  id uuid [pk, default: `gen_random_uuid()`]
  transfer_number varchar(50) [unique, not null]
  from_warehouse_id uuid [not null]
  to_warehouse_id uuid [not null]
  status stock_transfer_status [not null, default: 'PENDING']
  notes text
  created_by uuid [not null]
  shipped_at timestamp
  received_at timestamp
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'Inter-warehouse transfer - creates two inventory_transactions'
  Note: 'OUT from source warehouse, IN to destination warehouse'
  
  Check: 'from_warehouse_id != to_warehouse_id'
  
  Index: idx_stock_transfers_number on stock_transfers(transfer_number)
  Index: idx_stock_transfers_status on stock_transfers(status)
  Index: idx_stock_transfers_from_warehouse on stock_transfers(from_warehouse_id)
  Index: idx_stock_transfers_to_warehouse on stock_transfers(to_warehouse_id)
  Index: idx_stock_transfers_created on stock_transfers(created_at)
}

Table stock_transfer_items {
  id uuid [pk, default: `gen_random_uuid()`]
  stock_transfer_id uuid [not null]
  product_id uuid [not null]
  quantity integer [not null, check: 'quantity > 0']
  inventory_layer_id uuid [note: 'Tracks FIFO cost layer for transfer']
  notes text
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_stock_transfer_items_transfer on stock_transfer_items(stock_transfer_id)
  Index: idx_stock_transfer_items_product on stock_transfer_items(product_id)
}
```

---

## COMBO / BUNDLES

```dbml
Table combos {
  id uuid [pk, default: `gen_random_uuid()`]
  name varchar(255) [not null]
  description text
  is_active boolean [not null, default: true]
  version bigint [not null, default: 0]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  deleted_at timestamp
}

Table combo_items {
  id uuid [pk, default: `gen_random_uuid()`]
  combo_id uuid [not null]
  product_id uuid [not null]
  quantity integer [not null, check: 'quantity > 0']
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'UNIQUE (combo_id, product_id)'
}

Table combo_rules {
  id uuid [pk, default: `gen_random_uuid()`]
  combo_id uuid [unique, not null]
  rule_type combo_rule_type [not null]
  free_product_id uuid
  discount_type discount_type
  discount_value decimal(12,2) [check: 'discount_value IS NULL OR discount_value >= 0']
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
}
```

---

## CUSTOMERS

```dbml
Table customers {
  id uuid [pk, default: `gen_random_uuid()`]
  user_id uuid [unique]
  first_name varchar(100) [not null]
  last_name varchar(100) [not null]
  phone_number varchar(20) [unique, not null]
  email varchar(255)
  customer_code varchar(20) [unique, not null]
  address_id uuid [not null]
  total_orders integer [not null, default: 0, check: 'total_orders >= 0']
  credit_limit decimal(12,2) [not null, default: 0.00, check: 'credit_limit >= 0']
  outstanding_balance decimal(12,2) [not null, default: 0.00, check: 'outstanding_balance >= 0']
  advance_balance decimal(12,2) [not null, default: 0.00, check: 'advance_balance >= 0']
  balance_updated_at timestamp
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  deleted_at timestamp
  
  Index: idx_customers_user on customers(user_id)
  Index: idx_customers_phone on customers(phone_number)
  Index: idx_customers_code on customers(customer_code)
  Index: idx_customers_active_code on customers(customer_code) WHERE deleted_at IS NULL
}

Table customer_balance_cache {
  id uuid [pk, default: `gen_random_uuid()`]
  customer_id uuid [unique, not null]
  last_ledger_entry_id uuid
  balance decimal(12,2) [not null]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'Performance cache - not historical. Ledger is source of truth.'
  
  Index: idx_customer_balance_cache_customer on customer_balance_cache(customer_id)
}
```

---

## ORDERS

```dbml
Table orders {
  id uuid [pk, default: `gen_random_uuid()`]
  order_number varchar(50) [unique, not null]
  
  customer_id uuid [not null]
  agent_id uuid
  
  subtotal decimal(12,2) [not null, default: 0.00, check: 'subtotal >= 0']
  discount_amount decimal(12,2) [not null, default: 0.00, check: 'discount_amount >= 0']
  tax_amount decimal(12,2) [not null, default: 0.00, check: 'tax_amount >= 0']
  shipping_fee decimal(12,2) [not null, default: 0.00, check: 'shipping_fee >= 0']
  grand_total decimal(12,2) [not null, default: 0.00, check: 'grand_total >= 0']
  
  currency varchar(3) [not null, default: 'LKR']
  
  status order_status [not null, default: 'PENDING']
  source order_source [not null, default: 'AGENT']
  
  created_by uuid [not null]
  notes text
  
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_orders_number on orders(order_number)
  Index: idx_orders_customer on orders(customer_id)
  Index: idx_orders_customer_status_date on orders(customer_id, status, created_at)
  Index: idx_orders_agent on orders(agent_id)
  Index: idx_orders_status on orders(status)
  Index: idx_orders_created on orders(created_at)
}

Table order_items {
  id uuid [pk, default: `gen_random_uuid()`]
  order_id uuid [not null]
  
  product_id uuid
  combo_id uuid
  
  product_name varchar(255) [not null]
  product_sku varchar(100)
  
  quantity integer [not null, check: 'quantity > 0']
  unit_price decimal(12,2) [not null, check: 'unit_price >= 0']
  cost_price decimal(12,2) [check: 'cost_price IS NULL OR cost_price >= 0']
  
  discount_type discount_type
  discount_value decimal(12,2) [check: 'discount_value IS NULL OR discount_value >= 0']
  
  tax_rate decimal(5,4) [default: 0, check: 'tax_rate >= 0']
  tax_amount decimal(12,2) [not null, default: 0.00, check: 'tax_amount >= 0']
  
  subtotal decimal(12,2) [not null, check: 'subtotal >= 0']
  
  is_free_product boolean [not null, default: false]
  
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'CRITICAL: Actual PostgreSQL CHECK constraint (DBML notes are NOT enforced!)'
  
  Check: 'chk_order_items_product_or_combo' (
    (product_id IS NOT NULL AND combo_id IS NULL)
    OR
    (product_id IS NULL AND combo_id IS NOT NULL)
  )
  
  Index: idx_order_items_order on order_items(order_id)
  Index: idx_order_items_product on order_items(product_id)
}

Table order_status_history {
  id uuid [pk, default: `gen_random_uuid()`]
  order_id uuid [not null]
  status order_status [not null]
  remarks text
  changed_by uuid
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_order_status_order on order_status_history(order_id)
  Index: idx_order_status_date on order_status_history(created_at)
}

Table order_status_transition {
  id uuid [pk, default: `gen_random_uuid()`]
  from_status order_status [not null]
  to_status order_status [not null]
  is_allowed boolean [not null, default: true]
  
  Note: 'PRIMARY KEY (from_status, to_status)'
}
```

---

## DELIVERIES

```dbml
Table deliveries {
  id uuid [pk, default: `gen_random_uuid()`]
  order_id uuid [unique, not null]
  driver_id uuid
  
  status delivery_status [not null, default: 'PENDING']
  
  scheduled_date date
  assigned_at timestamp
  picked_up_at timestamp
  started_at timestamp
  delivered_at timestamp
  failed_at timestamp
  
  failure_reason text
  notes text
  driver_notes text
  
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_deliveries_order on deliveries(order_id)
  Index: idx_deliveries_driver on deliveries(driver_id)
  Index: idx_deliveries_driver_status on deliveries(driver_id, status)
  Index: idx_deliveries_status on deliveries(status)
  Index: idx_deliveries_scheduled on deliveries(scheduled_date)
}

Table delivery_proofs {
  id uuid [pk, default: `gen_random_uuid()`]
  delivery_id uuid [not null]
  
  type delivery_proof_type [not null]
  file_url varchar(1024) [not null]
  latitude decimal(10, 8)
  longitude decimal(11, 8)
  
  uploaded_by uuid [not null]
  
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_delivery_proofs_delivery on delivery_proofs(delivery_id)
}
```

---

## PAYMENTS

```dbml
Table payments {
  id uuid [pk, default: `gen_random_uuid()`]
  customer_id uuid [not null]
  payment_method_id uuid [not null]
  
  payment_number varchar(50) [unique, not null]
  
  provider varchar(50)
  currency varchar(3) [not null, default: 'LKR']
  
  total_amount decimal(12,2) [not null, check: 'total_amount > 0']
  
  status payment_status [not null, default: 'PENDING']
  
  gateway_reference varchar(255)
  external_transaction_id varchar(255)
  idempotency_key varchar(255) [unique]
  gateway_fee decimal(12,2) [default: 0, check: 'gateway_fee >= 0']
  gateway_response jsonb
  
  recorded_by uuid [not null]
  
  is_voided boolean [not null, default: false]
  voided_by uuid
  voided_at timestamp
  void_reason varchar(255)
  
  paid_at timestamp
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_payments_number on payments(payment_number)
  Index: idx_payments_customer on payments(customer_id)
  Index: idx_payments_method on payments(payment_method_id)
  Index: idx_payments_status on payments(status)
  Index: idx_payments_paid on payments(paid_at)
  Index: idx_payments_voided on payments(is_voided)
}

Table payment_status_transition {
  id uuid [pk, default: `gen_random_uuid()`]
  from_status payment_status [not null]
  to_status payment_status [not null]
  is_allowed boolean [not null, default: true]
  
  Note: 'PRIMARY KEY (from_status, to_status)'
}

Table payment_order_allocations {
  id uuid [pk, default: `gen_random_uuid()`]
  payment_id uuid [not null]
  order_id uuid [not null]
  allocated_amount decimal(12,2) [not null, check: 'allocated_amount > 0']
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'UNIQUE (payment_id, order_id)'
  
  Index: idx_payment_order_allocations_payment on payment_order_allocations(payment_id)
  Index: idx_payment_order_allocations_order on payment_order_allocations(order_id)
}

Table payment_return_allocations {
  id uuid [pk, default: `gen_random_uuid()`]
  payment_id uuid [not null]
  return_id uuid [not null]
  allocated_amount decimal(12,2) [not null, check: 'allocated_amount > 0']
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'UNIQUE (payment_id, return_id)'
  
  Index: idx_payment_return_allocations_payment on payment_return_allocations(payment_id)
  Index: idx_payment_return_allocations_return on payment_return_allocations(return_id)
}

Table payment_transactions {
  id uuid [pk, default: `gen_random_uuid()`]
  payment_id uuid [not null]
  parent_transaction_id uuid
  
  transaction_type payment_transaction_type [not null]
  amount decimal(12,2) [not null, check: 'amount != 0']
  status payment_status [not null]
  
  gateway_reference varchar(255)
  response_code varchar(50)
  response_message text
  raw_response jsonb
  
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_payment_txn_payment on payment_transactions(payment_id)
  Index: idx_payment_txn_payment_status on payment_transactions(payment_id, status)
  Index: idx_payment_txn_parent on payment_transactions(parent_transaction_id)
}
```

---

## RETURNS

```dbml
Table returns {
  id uuid [pk, default: `gen_random_uuid()`]
  return_number varchar(50) [unique, not null]
  
  order_id uuid [not null]
  customer_id uuid [not null]
  
  reason text [not null]
  status return_status [not null, default: 'PENDING']
  
  refund_amount decimal(12,2) [default: 0, check: 'refund_amount >= 0']
  restock_inventory boolean [not null, default: false]
  
  inspected_by uuid
  inspected_at timestamp
  approved_by uuid
  approved_at timestamp
  rejected_by uuid
  rejected_at timestamp
  
  notes text
  
  created_by uuid [not null]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_returns_number on returns(return_number)
  Index: idx_returns_order on returns(order_id)
  Index: idx_returns_customer on returns(customer_id)
  Index: idx_returns_status on returns(status)
}

Table return_items {
  id uuid [pk, default: `gen_random_uuid()`]
  return_id uuid [not null]
  order_item_id uuid [not null]
  
  quantity integer [not null, check: 'quantity > 0']
  refund_amount decimal(12,2) [not null, check: 'refund_amount >= 0']
  
  condition item_condition
  notes text
  
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'UNIQUE (return_id, order_item_id)'
  
  Index: idx_return_items_return on return_items(return_id)
}
```

---

## CUSTOMER LEDGER

*Corrected sign convention*

```dbml
Table customer_ledger {
  id uuid [pk, default: `gen_random_uuid()`]
  customer_id uuid [not null]
  
  order_id uuid
  payment_id uuid
  return_id uuid
  
  transaction_type ledger_transaction_type [not null]
  
  debit decimal(12,2) [default: 0, check: 'debit >= 0']
  credit decimal(12,2) [default: 0, check: 'credit >= 0']
  
  description text
  
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'Immutable - never update old rows. Only INSERT new.'
  Note: 'CORRECTED Sign Convention:'
  Note: '  Balance = SUM(debit) - SUM(credit)'
  Note: '  Debit = customer owes us (invoice created)'
  Note: '  Credit = customer payment received'
  Note: '  Example: Invoice $1000 (debit) - Payment $500 (credit) = $500 customer owes'
  Note: 'CHECK: (debit >= 0 AND credit >= 0) AND NOT (debit > 0 AND credit > 0)'
  
  Index: idx_ledger_customer on customer_ledger(customer_id)
  Index: idx_ledger_customer_date on customer_ledger(customer_id, created_at)
  Index: idx_ledger_order on customer_ledger(order_id)
  Index: idx_ledger_type on customer_ledger(transaction_type)
}
```

---

## SUPPLIER LEDGER

*Corrected sign convention*

```dbml
Table supplier_ledger {
  id uuid [pk, default: `gen_random_uuid()`]
  supplier_id uuid [not null]
  
  purchase_order_id uuid
  expense_id uuid
  
  transaction_type ledger_transaction_type [not null]
  
  debit decimal(12,2) [default: 0, check: 'debit >= 0']
  credit decimal(12,2) [default: 0, check: 'credit >= 0']
  
  description text
  
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'Immutable - never update old rows. Only INSERT new.'
  Note: 'CORRECTED Sign Convention:'
  Note: '  Balance (we owe) = SUM(credit) - SUM(debit)'
  Note: '  Credit = we owe supplier (PO received)'
  Note: '  Debit = we paid supplier'
  Note: '  Example: PO $1000 (credit) - Payment $500 (debit) = $500 we owe supplier'
  Note: 'CHECK: (debit >= 0 AND credit >= 0) AND NOT (debit > 0 AND credit > 0)'
  
  Index: idx_supplier_ledger_supplier on supplier_ledger(supplier_id)
  Index: idx_supplier_ledger_date on supplier_ledger(supplier_id, created_at)
  Index: idx_supplier_ledger_po on supplier_ledger(purchase_order_id)
  Index: idx_supplier_ledger_expense on supplier_ledger(expense_id)
}

Table supplier_balance_cache {
  id uuid [pk, default: `gen_random_uuid()`]
  supplier_id uuid [unique, not null]
  last_ledger_entry_id uuid
  balance decimal(12,2) [not null]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'Performance cache - not historical. Ledger is source of truth.'
  
  Index: idx_supplier_balance_cache_supplier on supplier_balance_cache(supplier_id)
}
```

---

## INVOICES

```dbml
Table invoices {
  id uuid [pk, default: `gen_random_uuid()`]
  invoice_number varchar(50) [unique, not null]
  order_id uuid [not null]
  customer_id uuid [not null]
  status invoice_status [not null, default: 'DRAFT']
  issued_at timestamp
  due_date date
  sent_at timestamp
  paid_at timestamp
  voided_at timestamp
  void_reason varchar(255)
  total decimal(12,2) [not null, check: 'total >= 0']
  paid_amount decimal(12,2) [not null, default: 0, check: 'paid_amount >= 0']
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_invoices_number on invoices(invoice_number)
  Index: idx_invoices_order on invoices(order_id)
  Index: idx_invoices_customer on invoices(customer_id)
  Index: idx_invoices_status on invoices(status)
}
```

---

## CREDIT NOTES

```dbml
Table credit_notes {
  id uuid [pk, default: `gen_random_uuid()`]
  credit_note_number varchar(50) [unique, not null]
  customer_id uuid [not null]
  order_id uuid
  amount decimal(12,2) [not null, check: 'amount > 0']
  reason text
  status credit_note_status [not null, default: 'DRAFT']
  applied_amount decimal(12,2) [not null, default: 0, check: 'applied_amount >= 0']
  voided_at timestamp
  created_by uuid [not null]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_credit_notes_number on credit_notes(credit_note_number)
  Index: idx_credit_notes_customer on credit_notes(customer_id)
  Index: idx_credit_notes_order on credit_notes(order_id)
}
```

---

## COMMISSIONS

```dbml
Table commission_configs {
  id uuid [pk, default: `gen_random_uuid()`]
  name varchar(255) [unique, not null]
  commission_rate decimal(12,2) [not null, check: 'commission_rate >= 0']
  type commission_type [not null, default: 'DEFAULT']
  min_amount decimal(12,2) [default: 0.0, check: 'min_amount >= 0']
  max_amount decimal(12,2) [check: 'max_amount IS NULL OR max_amount >= 0']
  applies_to varchar(255) [not null]
  is_active boolean [not null, default: true]
  effective_from timestamp [not null]
  effective_to timestamp
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
}

Table commissions {
  id uuid [pk, default: `gen_random_uuid()`]
  agent_id uuid [not null]
  order_id uuid [not null]
  config_id uuid
  order_amount decimal(12,2) [not null, check: 'order_amount >= 0']
  commission_rate decimal(12,2) [not null, check: 'commission_rate >= 0']
  commission_amount decimal(12,2) [not null, check: 'commission_amount >= 0']
  status commission_status [not null, default: 'PENDING']
  calculated_at timestamp
  approved_at timestamp
  paid_at timestamp
  notes text
  error_message text
  version bigint [not null, default: 0]
  
  is_voided boolean [not null, default: false]
  voided_by uuid
  voided_at timestamp
  
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_commissions_agent on commissions(agent_id)
  Index: idx_commissions_order on commissions(order_id)
  Index: idx_commissions_status on commissions(status)
  Index: idx_commissions_voided on commissions(is_voided)
}

Table payouts {
  id uuid [pk, default: `gen_random_uuid()`]
  agent_id uuid [not null]
  total_amount decimal(12,2) [not null, check: 'total_amount >= 0']
  status payout_status [not null, default: 'PENDING']
  paid_at timestamp
  pdf_url varchar(500)
  version bigint [not null, default: 0]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_payouts_agent on payouts(agent_id)
  Index: idx_payouts_status on payouts(status)
}

Table payout_commission_ids {
  id uuid [pk, default: `gen_random_uuid()`]
  payout_id uuid [not null]
  commission_id uuid [not null]
  
  Note: 'UNIQUE (payout_id, commission_id)'
}

Table agent_ledger {
  id uuid [pk, default: `gen_random_uuid()`]
  agent_id uuid [not null]
  commission_id uuid
  payout_id uuid
  transaction_type varchar(50) [not null]
  debit decimal(12,2) [default: 0, check: 'debit >= 0']
  credit decimal(12,2) [default: 0, check: 'credit >= 0']
  description text
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'Immutable - never update old rows. Only INSERT new.'
  Note: 'Agent Balance = SUM(credit) - SUM(debit)'
  Note: 'Credit = commission earned, Debit = commission paid'
  
  Index: idx_agent_ledger_agent on agent_ledger(agent_id)
  Index: idx_agent_ledger_agent_date on agent_ledger(agent_id, created_at)
  Index: idx_agent_ledger_commission on agent_ledger(commission_id)
  Index: idx_agent_ledger_payout on agent_ledger(payout_id)
}

Table agent_balance_cache {
  id uuid [pk, default: `gen_random_uuid()`]
  agent_id uuid [unique, not null]
  last_ledger_entry_id uuid
  balance decimal(12,2) [not null]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_agent_balance_cache_agent on agent_balance_cache(agent_id)
}
```

---

## PURCHASE ORDERS / SUPPLIERS

```dbml
Table suppliers {
  id uuid [pk, default: `gen_random_uuid()`]
  name varchar(255) [not null]
  contact_person varchar(255)
  email varchar(255)
  phone varchar(20)
  address_id uuid [not null]
  is_active boolean [not null, default: true]
  outstanding_balance decimal(12,2) [not null, default: 0.00, check: 'outstanding_balance >= 0']
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  deleted_at timestamp
  
  Index: idx_suppliers_name on suppliers(name)
  Index: idx_suppliers_active on suppliers(is_active)
  Index: idx_suppliers_active_name on suppliers(name) WHERE deleted_at IS NULL AND is_active = true
}

Table purchase_orders {
  id uuid [pk, default: `gen_random_uuid()`]
  po_number varchar(50) [unique, not null]
  supplier_id uuid [not null]
  status purchase_order_status [not null, default: 'DRAFT']
  total_amount decimal(12,2) [not null, default: 0, check: 'total_amount >= 0']
  notes text
  created_by uuid
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  version bigint [not null, default: 0]
  
  Index: idx_po_number on purchase_orders(po_number)
  Index: idx_po_supplier on purchase_orders(supplier_id)
  Index: idx_po_status on purchase_orders(status)
}

Table purchase_order_items {
  id uuid [pk, default: `gen_random_uuid()`]
  purchase_order_id uuid [not null]
  product_id uuid [not null]
  quantity integer [not null, check: 'quantity > 0']
  unit_price decimal(12,2) [not null, check: 'unit_price >= 0']
  subtotal decimal(12,2) [not null, check: 'subtotal >= 0']
  selling_price decimal(12,2) [check: 'selling_price IS NULL OR selling_price >= 0']
  returned_quantity integer [not null, default: 0, check: 'returned_quantity >= 0']
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_po_items_order on purchase_order_items(purchase_order_id)
}

Table purchase_order_attachments {
  id uuid [pk, default: `gen_random_uuid()`]
  purchase_order_id uuid [not null]
  file_url varchar(1024) [not null]
  file_name varchar(255)
  file_type varchar(100)
  file_size bigint
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
}

Table purchase_receipts {
  id uuid [pk, default: `gen_random_uuid()`]
  purchase_order_id uuid [not null]
  receipt_number varchar(50) [unique, not null]
  
  status purchase_receipt_status [not null, default: 'DRAFT']
  
  received_by uuid
  
  notes text
  
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_receipts_order on purchase_receipts(purchase_order_id)
  Index: idx_receipts_status on purchase_receipts(status)
}

Table purchase_receipt_items {
  id uuid [pk, default: `gen_random_uuid()`]
  purchase_receipt_id uuid [not null]
  purchase_order_item_id uuid [not null]
  
  quantity_received integer [not null, check: 'quantity_received > 0']
  condition varchar(50)
  
  notes text
  
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_receipt_items_receipt on purchase_receipt_items(purchase_receipt_id)
}

Table supplier_payments {
  id uuid [pk, default: `gen_random_uuid()`]
  supplier_id uuid [not null]
  
  amount decimal(12,2) [not null, check: 'amount > 0']
  payment_method payment_method_type [not null]
  
  reference_number varchar(100)
  notes text
  
  status purchase_payment_status [not null, default: 'UNPAID']
  paid_at timestamp
  
  is_voided boolean [not null, default: false]
  voided_by uuid
  voided_at timestamp
  void_reason varchar(255)
  
  created_by uuid [not null]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_supplier_payments_supplier on supplier_payments(supplier_id)
  Index: idx_supplier_payments_status on supplier_payments(status)
  Index: idx_supplier_payments_voided on supplier_payments(is_voided)
}

Table supplier_payment_allocations {
  id uuid [pk, default: `gen_random_uuid()`]
  supplier_payment_id uuid [not null]
  purchase_order_id uuid [not null]
  allocated_amount decimal(12,2) [not null, check: 'allocated_amount > 0']
  
  Note: 'UNIQUE (supplier_payment_id, purchase_order_id)'
  
  Index: idx_supplier_payment_allocations_payment on supplier_payment_allocations(supplier_payment_id)
  Index: idx_supplier_payment_allocations_po on supplier_payment_allocations(purchase_order_id)
}
```

---

## LEADS

```dbml
Table leads {
  id uuid [pk, default: `gen_random_uuid()`]
  agent_id uuid [not null]
  customer_name varchar(255) [not null]
  phone varchar(50) [not null]
  email varchar(255)
  product_interest varchar(255) [not null]
  note text
  submitted_at timestamp [not null]
  converted_order_id uuid
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'UNIQUE (agent_id, phone)'
  
  Index: idx_leads_agent on leads(agent_id)
  Index: idx_leads_phone on leads(phone)
  Index: idx_leads_agent_phone on leads(agent_id, phone)
}
```

---

## EXPENSES

```dbml
Table expenses {
  id uuid [pk, default: `gen_random_uuid()`]
  category expense_category [not null]
  amount decimal(12,2) [not null, check: 'amount > 0']
  description text
  date_incurred date [not null]
  status expense_status [not null, default: 'PENDING']
  created_by uuid [not null]
  driver_id uuid
  approved_by uuid
  approved_at timestamp
  rejection_reason text
  paid_at timestamp
  payment_reference varchar(255)
  expense_supplier_id uuid
  version bigint [not null, default: 0]
  
  is_voided boolean [not null, default: false]
  voided_by uuid
  voided_at timestamp
  void_reason varchar(255)
  
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_expenses_creator on expenses(created_by)
  Index: idx_expenses_driver on expenses(driver_id)
  Index: idx_expenses_status on expenses(status)
  Index: idx_expenses_date on expenses(date_incurred)
  Index: idx_expenses_voided on expenses(is_voided)
  Index: idx_expenses_supplier on expenses(expense_supplier_id)
}

Table expense_attachments {
  id uuid [pk, default: `gen_random_uuid()`]
  expense_id uuid [not null]
  file_url varchar(1024) [not null]
  file_name varchar(255)
  file_type varchar(100)
  file_size bigint
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_expense_attachments_expense on expense_attachments(expense_id)
}
```

---

## ACCOUNTING / GL

*With fiscal years and accounting periods*

```dbml
Table fiscal_years {
  id uuid [pk, default: `gen_random_uuid()`]
  year integer [unique, not null, check: 'year >= 2000 AND year <= 2100']
  name varchar(50) [not null]
  start_date date [not null]
  end_date date [not null]
  is_closed boolean [not null, default: false]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'Fiscal year for accounting period control'
  Note: 'Closed fiscal years prevent editing past accounting data'
  
  Check: 'start_date < end_date'
  
  Index: idx_fiscal_years_year on fiscal_years(year)
  Index: idx_fiscal_years_closed on fiscal_years(is_closed)
}

Table accounting_periods {
  id uuid [pk, default: `gen_random_uuid()`]
  fiscal_year_id uuid [not null]
  month integer [not null, check: 'month >= 1 AND month <= 12']
  year integer [not null]
  status period_status [not null, default: 'OPEN']
  opened_at timestamp
  closed_at timestamp
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'Monthly accounting period'
  Note: 'OPEN = can post journal entries'
  Note: 'CLOSED = cannot post, but can reopen via super_admin'
  Note: 'ARCHIVED = permanent, cannot post or reopen'
  Note: 'Single company - one active fiscal year at a time'
  
  Unique: '(fiscal_year_id, month)'
  
  Index: idx_accounting_periods_fiscal_year on accounting_periods(fiscal_year_id)
  Index: idx_accounting_periods_year_month on accounting_periods(year, month)
  Index: idx_accounting_periods_status on accounting_periods(status)
}

Table accounting_period_audit {
  id uuid [pk, default: `gen_random_uuid()`]
  period_id uuid [not null]
  action varchar(50) [not null]
  old_status period_status
  new_status period_status [not null]
  reason varchar(255) [not null]
  performed_by uuid [not null]
  performed_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'Audit trail for period status changes'
  Note: 'Tracks CLOSE, REOPEN, ARCHIVE actions'
  
  Index: idx_accounting_period_audit_period on accounting_period_audit(period_id)
  Index: idx_accounting_period_audit_performer on accounting_period_audit(performed_by)
}

Table chart_of_accounts {
  id uuid [pk, default: `gen_random_uuid()`]
  name varchar(100) [unique, not null]
  description text
  category chart_of_accounts_category [not null]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_chart_of_accounts_category on chart_of_accounts(category)
}

Table accounts {
  id uuid [pk, default: `gen_random_uuid()`]
  chart_of_accounts_id uuid [not null]
  code varchar(20) [unique, not null]
  name varchar(100) [not null]
  account_type account_type [not null]
  parent_id uuid
  is_active boolean [default: true]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_accounts_chart on accounts(chart_of_accounts_id)
  Index: idx_accounts_code on accounts(code)
}

Table journal_entries {
  id uuid [pk, default: `gen_random_uuid()`]
  entry_number varchar(50) [unique, not null]
  description text
  entry_date date [not null]
  fiscal_year_id uuid [not null]
  period_id uuid [not null]
  status journal_status [not null, default: 'DRAFT']
  posted_at timestamp
  created_by uuid [not null]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'Immutable after posting'
  Note: 'Period_id links to accounting_periods - DB trigger prevents closed period posting'
  Note: 'Service layer also validates period is OPEN before posting'
  
  Index: idx_journal_entries_date on journal_entries(entry_date)
  Index: idx_journal_entries_status on journal_entries(status)
  Index: idx_journal_entries_fiscal on journal_entries(fiscal_year_id)
  Index: idx_journal_entries_period on journal_entries(period_id)
}

Table journal_lines {
  id uuid [pk, default: `gen_random_uuid()`]
  journal_entry_id uuid [not null]
  account_id uuid [not null]
  debit decimal(12,2) [default: 0, check: 'debit >= 0']
  credit decimal(12,2) [default: 0, check: 'credit >= 0']
  description text
  
  Note: 'CHECK: (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)'
  
  Index: idx_journal_lines_entry on journal_lines(journal_entry_id)
  Index: idx_journal_lines_account on journal_lines(account_id)
}

Table general_ledger {
  id uuid [pk, default: `gen_random_uuid()`]
  account_id uuid [not null]
  journal_entry_id uuid [not null]
  debit decimal(12,2) [default: 0, check: 'debit >= 0']
  credit decimal(12,2) [default: 0, check: 'credit >= 0']
  running_balance decimal(12,2) [not null]
  entry_date date [not null]
  fiscal_year_id uuid [not null]
  period_id uuid [not null]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'Immutable - never update old rows'
  Note: 'Balance = SUM(debit) - SUM(credit) by account'
  Note: 'Period_id links to accounting_periods - DB trigger prevents closed period posting'
  Note: 'CHECK: (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)'
  
  Index: idx_general_ledger_account on general_ledger(account_id)
  Index: idx_general_ledger_date on general_ledger(entry_date)
  Index: idx_general_ledger_fiscal on general_ledger(fiscal_year_id)
  Index: idx_general_ledger_period on general_ledger(period_id)
}

Table account_balances {
  id uuid [pk, default: `gen_random_uuid()`]
  account_id uuid [not null]
  fiscal_year_id uuid [not null]
  period_id uuid [not null]
  balance decimal(12,2) [not null]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'Performance cache for account balances by period'
  
  Unique: '(account_id, fiscal_year_id, period_id)'
  
  Index: idx_account_balances_account_period on account_balances(account_id, fiscal_year_id, period_id)
}
```

---

## NOTIFICATIONS & MESSAGING

```dbml
Table reminders {
  id uuid [pk, default: `gen_random_uuid()`]
  user_id uuid [not null]
  title varchar(255) [not null]
  description text
  reminder_date date [not null]
  reminder_time time
  status reminder_status [not null, default: 'PENDING']
  priority reminder_priority [not null, default: 'MEDIUM']
  category varchar(100)
  send_email boolean [not null, default: false]
  send_sms boolean [not null, default: false]
  early_alert_sent boolean [not null, default: false]
  alert_sent boolean [not null, default: false]
  notified_at timestamp
  completed_at timestamp
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_reminders_user_date on reminders(user_id, reminder_date)
  Index: idx_reminders_status on reminders(status)
}

Table alerts {
  id uuid [pk, default: `gen_random_uuid()`]
  recipient_id uuid [not null]
  title varchar(255) [not null]
  message text
  alert_type alert_type [not null]
  reference_id uuid
  reference_type varchar(50)
  is_read boolean [not null, default: false]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_alerts_recipient on alerts(recipient_id)
  Index: idx_alerts_unread on alerts(recipient_id, is_read)
}

Table messages {
  id uuid [pk, default: `gen_random_uuid()`]
  sent_by uuid [not null]
  subject varchar(255) [not null]
  body text [not null]
  target_type message_target_type [not null]
  total_recipients integer [not null, default: 0, check: 'total_recipients >= 0']
  sent_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Index: idx_messages_sender on messages(sent_by)
}

Table message_recipients {
  id uuid [pk, default: `gen_random_uuid()`]
  message_id uuid [not null]
  recipient_type varchar(10) [not null]
  recipient_id uuid [not null]
  name varchar(255)
  phone_number varchar(20)
  email varchar(255)
  sent_sms boolean [not null, default: false]
  sent_email boolean [not null, default: false]
  
  Index: idx_message_recipients_msg on message_recipients(message_id)
}
```

---

## UNIFIED AUDIT

*Partitioning guidance included*

```dbml
Table audit_entries {
  id uuid [pk, default: `gen_random_uuid()`]
  
  entity_type varchar(100) [not null]
  entity_id uuid [not null]
  
  category audit_category [not null]
  
  action varchar(50) [not null]
  
  field_name varchar(100)
  old_value text
  new_value text
  
  before_state jsonb
  after_state jsonb
  
  changed_by uuid [not null]
  ip_address varchar(45)
  
  reason varchar(255)
  metadata jsonb
  
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
  
  Note: 'FIX #7: Production deployment requires PARTITION BY RANGE (created_at)'
  Note: 'PostgreSQL 10+: CREATE TABLE audit_entries (...) PARTITION BY RANGE (created_at)'
  Note: 'Monthly partitions: audit_entries_2026_01, audit_entries_2026_02, etc.'
  Note: 'Partial indexes defined below optimize common queries'
  
  Index: idx_audit_entity on audit_entries(entity_type, entity_id)
  Index: idx_audit_entity_date on audit_entries(entity_type, entity_id, created_at)
  Index: idx_audit_category on audit_entries(category)
  Index: idx_audit_action on audit_entries(action)
  Index: idx_audit_changed_by on audit_entries(changed_by)
  Index: idx_audit_time on audit_entries(created_at)
}
```

---

## SYSTEM TABLES

```dbml
Table backup_audit_logs {
  id uuid [pk]
  action varchar(20) [not null]
  filename varchar(255)
  detail text
  performed_by uuid [not null]
  created_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
}

Table shedlock {
  name varchar(64) [pk]
  lock_until timestamp [not null]
  locked_at timestamp [not null]
  locked_by varchar(255) [not null]
}

Table code_sequence {
  code varchar(20) [pk]
  prefix varchar(10) [not null]
  current_value bigint [not null, default: 0]
  format_template varchar(50) [default: '{prefix}{zero_padded_value}']
}

Table whitelist_config {
  id integer [pk, default: 1]
  enabled boolean [not null, default: true]
  auto_close_day integer [default: 5]
  updated_at timestamp [not null, default: `CURRENT_TIMESTAMP`]
}
```

---

## RELATIONSHIPS

```dbml
// ---- Users & Auth ----
Ref: agents.user_id > users.id
Ref: agents.address_id > addresses.id
Ref: drivers.user_id > users.id
Ref: ip_whitelist.user_id > users.id
Ref: ip_whitelist.created_by > users.id
Ref: agent_documents.agent_id > agents.id
Ref: user_sessions.user_id > users.id

// ---- Products & Inventory ----
Ref: products.category_id > categories.id
Ref: products.tax_rate_id > tax_rates.id
Ref: product_tags.product_id > products.id
Ref: product_tags.tag_id > tags.id
Ref: product_images.product_id > products.id
Ref: inventory.product_id > products.id
Ref: inventory.warehouse_id > warehouses.id
Ref: inventory_layers.inventory_id > inventory.id
Ref: inventory_layer_transactions.inventory_layer_id > inventory_layers.id
Ref: inventory_layer_transactions.created_by > users.id
Ref: inventory_transactions.inventory_id > inventory.id
Ref: inventory_transactions.warehouse_id > warehouses.id
Ref: inventory_transactions.created_by > users.id
Ref: inventory_reservations.inventory_id > inventory.id
Ref: inventory_reservations.order_id > orders.id

// ---- Stock Transfers ----
Ref: stock_transfers.from_warehouse_id > warehouses.id
Ref: stock_transfers.to_warehouse_id > warehouses.id
Ref: stock_transfers.created_by > users.id
Ref: stock_transfer_items.stock_transfer_id > stock_transfers.id
Ref: stock_transfer_items.product_id > products.id
Ref: stock_transfer_items.inventory_layer_id > inventory_layers.id

// ---- Combo ----
Ref: combos.id < combo_items.combo_id
Ref: combos.id < combo_rules.combo_id
Ref: combo_items.product_id > products.id
Ref: combo_rules.free_product_id > products.id

// ---- Customers ----
Ref: customers.user_id > users.id
Ref: customers.address_id > addresses.id
Ref: customer_balance_cache.customer_id > customers.id

// ---- Orders ----
Ref: orders.customer_id > customers.id
Ref: orders.agent_id > agents.id
Ref: orders.created_by > users.id

Ref: order_items.order_id > orders.id
Ref: order_items.product_id > products.id
Ref: order_items.combo_id > combos.id

Ref: order_status_history.order_id > orders.id
Ref: order_status_history.changed_by > users.id

// ---- Deliveries ----
Ref: deliveries.order_id > orders.id
Ref: deliveries.driver_id > drivers.id
Ref: delivery_proofs.delivery_id > deliveries.id
Ref: delivery_proofs.uploaded_by > users.id

// ---- Payments ----
Ref: payments.customer_id > customers.id
Ref: payments.payment_method_id > payment_methods.id
Ref: payments.recorded_by > users.id
Ref: payments.voided_by > users.id

Ref: payment_order_allocations.payment_id > payments.id
Ref: payment_order_allocations.order_id > orders.id

Ref: payment_return_allocations.payment_id > payments.id
Ref: payment_return_allocations.return_id > returns.id

Ref: payment_transactions.payment_id > payments.id
Ref: payment_transactions.parent_transaction_id > payment_transactions.id

// ---- Returns ----
Ref: returns.order_id > orders.id
Ref: returns.customer_id > customers.id
Ref: returns.created_by > users.id
Ref: returns.inspected_by > users.id
Ref: returns.approved_by > users.id
Ref: returns.rejected_by > users.id

Ref: return_items.return_id > returns.id
Ref: return_items.order_item_id > order_items.id

// ---- Customer Ledger ----
Ref: customer_ledger.customer_id > customers.id
Ref: customer_ledger.order_id > orders.id
Ref: customer_ledger.payment_id > payments.id
Ref: customer_ledger.return_id > returns.id
Ref: customer_balance_cache.last_ledger_entry_id > customer_ledger.id

// ---- Supplier Ledger ----
Ref: supplier_ledger.supplier_id > suppliers.id
Ref: supplier_ledger.purchase_order_id > purchase_orders.id
Ref: supplier_ledger.expense_id > expenses.id
Ref: supplier_balance_cache.supplier_id > suppliers.id
Ref: supplier_balance_cache.last_ledger_entry_id > supplier_ledger.id

// ---- Invoices ----
Ref: invoices.order_id > orders.id
Ref: invoices.customer_id > customers.id

// ---- Credit Notes ----
Ref: credit_notes.customer_id > customers.id
Ref: credit_notes.order_id > orders.id
Ref: credit_notes.created_by > users.id

// ---- Commissions ----
Ref: commissions.agent_id > agents.id
Ref: commissions.order_id > orders.id
Ref: commissions.config_id > commission_configs.id
Ref: commissions.voided_by > users.id
Ref: payouts.agent_id > agents.id
Ref: payout_commission_ids.payout_id > payouts.id
Ref: payout_commission_ids.commission_id > commissions.id

// ---- Agent Ledger ----
Ref: agent_ledger.agent_id > agents.id
Ref: agent_ledger.commission_id > commissions.id
Ref: agent_ledger.payout_id > payouts.id
Ref: agent_balance_cache.agent_id > agents.id
Ref: agent_balance_cache.last_ledger_entry_id > agent_ledger.id

// ---- Purchase Orders ----
Ref: suppliers.address_id > addresses.id
Ref: purchase_orders.supplier_id > suppliers.id
Ref: purchase_orders.created_by > users.id
Ref: purchase_order_items.purchase_order_id > purchase_orders.id
Ref: purchase_order_items.product_id > products.id
Ref: purchase_order_attachments.purchase_order_id > purchase_orders.id
Ref: purchase_receipts.purchase_order_id > purchase_orders.id
Ref: purchase_receipts.received_by > users.id
Ref: purchase_receipt_items.purchase_receipt_id > purchase_receipts.id
Ref: purchase_receipt_items.purchase_order_item_id > purchase_order_items.id

Ref: supplier_payments.supplier_id > suppliers.id
Ref: supplier_payments.created_by > users.id
Ref: supplier_payments.voided_by > users.id

Ref: supplier_payment_allocations.supplier_payment_id > supplier_payments.id
Ref: supplier_payment_allocations.purchase_order_id > purchase_orders.id

// ---- Leads ----
Ref: leads.agent_id > agents.id
Ref: leads.converted_order_id > orders.id

// ---- Expenses ----
Ref: expenses.created_by > users.id
Ref: expenses.driver_id > drivers.id
Ref: expenses.approved_by > users.id
Ref: expenses.voided_by > users.id
Ref: expenses.expense_supplier_id > suppliers.id
Ref: expense_attachments.expense_id > expenses.id

// ---- Accounting ----
Ref: fiscal_years.id < accounting_periods.fiscal_year_id
Ref: accounting_period_audit.period_id > accounting_periods.id
Ref: accounting_period_audit.performed_by > users.id
Ref: chart_of_accounts.id < accounts.chart_of_accounts_id
Ref: accounts.parent_id > accounts.id
Ref: journal_entries.fiscal_year_id > fiscal_years.id
Ref: journal_entries.period_id > accounting_periods.id
Ref: journal_entries.created_by > users.id
Ref: journal_lines.journal_entry_id > journal_entries.id
Ref: journal_lines.account_id > accounts.id
Ref: general_ledger.account_id > accounts.id
Ref: general_ledger.journal_entry_id > journal_entries.id
Ref: general_ledger.fiscal_year_id > fiscal_years.id
Ref: general_ledger.period_id > accounting_periods.id
Ref: account_balances.account_id > accounts.id
Ref: account_balances.fiscal_year_id > fiscal_years.id
Ref: account_balances.period_id > accounting_periods.id

// ---- Notifications ----
Ref: reminders.user_id > users.id
Ref: alerts.recipient_id > users.id
Ref: messages.sent_by > users.id
Ref: message_recipients.message_id > messages.id

// ---- Audit ----
Ref: audit_entries.changed_by > users.id
Ref: backup_audit_logs.performed_by > users.id
```

---

## MIGRATIONS SUMMARY

| Migration | Description |
|-----------|-------------|
| **V91** | Add inventory locking - `@Lock(PESSIMISTIC_WRITE)` queries to InventoryRepository |
| **V92** | Add actual CHECK constraint to `order_items` table |
| **V93** | Create `fiscal_years`, `accounting_periods`, `accounting_period_audit` tables |
| **V94** | Add DB triggers to prevent journal entries in closed periods |
| **V95** | Create `stock_transfers` and `stock_transfer_items` tables |
| **V96** | Add partial indexes for soft delete queries |
| **V97** | Document partitioning strategy for `audit_entries` |

---

## KEY DESIGN DECISIONS

### 1. Inventory Locking
All stock-modifying methods in `InventoryService` must use `SELECT FOR UPDATE` to prevent race conditions.

### 2. Order Items CHECK Constraint
DBML notes are NOT enforced by PostgreSQL. Actual CHECK constraint added via migration.

### 3. Ledger Sign Convention
- **Customer Ledger**: `Balance = SUM(debit) - SUM(credit)`
  - Debit = customer owes us
  - Credit = customer payment
- **Supplier Ledger**: `Balance (we owe) = SUM(credit) - SUM(debit)`
  - Credit = we owe supplier
  - Debit = we paid supplier

### 4. Accounting Period Control
- **OPEN**: Can post journal entries
- **CLOSED**: Cannot post, can reopen via super_admin
- **ARCHIVED**: Permanent, cannot post or reopen

### 5. Stock Transfers
Creates two `inventory_transactions` per transfer: OUT from source, IN to destination.

### 6. Audit Partitioning
Production requires `PARTITION BY RANGE (created_at)` with monthly partitions.

---

## AUTOMATION REQUIREMENTS

All business events must trigger automatic updates. No manual intervention.

---

### A. EVENT-DRIVEN AUDIT SYSTEM

Every CREATE, UPDATE, DELETE on major entities must automatically write to `audit_entries`.

**Implementation:** JPA Entity Listeners + Spring Events

```java
// BaseEntityListener attached to all auditable entities
@PrePersist
void prePersist(Object entity) {
    eventPublisher.publishEvent(new AuditEvent(AuditAction.CREATE, entity));
}

@PreUpdate
void preUpdate(Object entity) {
    eventPublisher.publishEvent(new AuditEvent(AuditAction.UPDATE, entity));
}

@PreRemove
void preRemove(Object entity) {
    eventPublisher.publishEvent(new AuditEvent(AuditAction.DELETE, entity));
}
```

**Auditable Entities:**
- Orders, OrderItems
- Payments, PaymentAllocations
- Customers, Suppliers
- Products, Inventory
- Commissions, Payouts
- Expenses
- PurchaseOrders, PurchaseReceipts
- Any entity with financial impact

---

### B. AUTOMATIC LEDGER ENTRIES

Every financial transaction must automatically create ledger entries.

#### B1. Customer Ledger Automation

| Event | Debit | Credit | Balance Formula |
|-------|-------|--------|-----------------|
| Order Created (invoice) | grand_total | 0 | +grand_total |
| Payment Received | 0 | amount | -amount |
| Return Refunded (advance) | 0 | amount | -amount |
| Return Refunded (cash) | amount | 0 | +amount |
| Advance Used | 0 | amount | -amount |
| Credit Note Applied | 0 | amount | -amount |

**Trigger:** `CustomerLedgerService.recordOrderEntry()`, `recordPaymentEntry()`

#### B2. Supplier Ledger Automation

| Event | Debit | Credit | Balance Formula |
|-------|-------|--------|-----------------|
| PO Received (goods received) | 0 | total | +total (we owe) |
| Return to Supplier | return_amount | 0 | -return_amount |
| Payment to Supplier | amount | 0 | -amount |
| Expense (if supplier billed) | expense_amount | 0 | -expense_amount |

**Trigger:** `SupplierLedgerService.recordPurchaseEntry()`, `recordPaymentEntry()`

---

### C. AUTOMATIC JOURNAL ENTRIES (GL)

Every financial transaction must create journal entries with proper accounts.

#### C1. Sales Journal Entry (on Order Delivered)
```
DR  Accounts Receivable    $1000
    CR  Sales Revenue           $1000
```

#### C2. Cash Receipt (on Payment)
```
DR  Cash/Bank            $1000
    CR  Accounts Receivable     $1000
```

#### C3. Inventory (on COGS)
```
DR  Cost of Goods Sold   $700
    CR  Inventory              $700
```

#### C4. Purchase (on PO Received)
```
DR  Inventory            $800
    CR  Accounts Payable      $800
```

#### C5. Supplier Payment
```
DR  Accounts Payable     $800
    CR  Cash/Bank               $800
```

#### C6. Commission (on Order Delivered)
```
DR  Commission Expense    $100
    CR  Commission Payable      $100
```

#### C7. Commission Paid
```
DR  Commission Payable   $100
    CR  Cash/Bank               $100
```

**Trigger:** `JournalEntryService.createJournalEntry()` called by event listeners

---

### D. ACCOUNT AUTOMATIONS

#### D1. Auto-Update Balance Cache
After every ledger/journal entry, update `account_balances` cache:
```java
@EventListener
void onLedgerEntry(LedgerEntryEvent event) {
    accountBalanceService.updateBalance(event.getAccountId(), event.getAmount());
}
```

#### D2. Auto-Recalculate Customer/Supplier Outstanding
After every ledger entry:
```java
customerBalanceCacheService.recalculate(customerId);
// Reads: SUM(debit) - SUM(credit) from customer_ledger
```

---

### E. STOCK TRANSFER AUTOMATION

When `StockTransfer` status changes:

| Status Change | Automatic Actions |
|--------------|------------------|
| CREATED | Validate inventory available at source warehouse |
| IN_TRANSIT | Create `inventory_transactions` with direction=OUT for source warehouse |
| COMPLETED | Create `inventory_transactions` with direction=IN for destination warehouse, consume FIFO layers |
| CANCELLED | If IN_TRANSIT, reverse the OUT transaction |

**Trigger:** `StockTransferService.updateStatus()`

---

### F. INVENTORY LOCKING AUTOMATION

All stock-modifying operations MUST acquire row-level lock before read:

```java
// In InventoryRepository
@Query("SELECT i FROM Inventory i WHERE i.product.id = :productId")
Optional<Inventory> findByProductIdWithLock(@Param("productId") UUID productId);

// Native query for explicit FOR UPDATE
@Query(value = "SELECT * FROM inventory WHERE product_id = :productId FOR UPDATE", nativeQuery = true)
Optional<Inventory> findByProductIdWithPessimisticLock(@Param("productId") UUID productId);
```

**Operations requiring lock:**
- `reserveStock()`
- `confirmOrderStock()`
- `recordOrderStocks()`
- `adjustStock()`
- `recordPurchaseStocks()`

---

### G. PERIOD AUTOMATION

#### G1. Auto-Close Periods
Daily scheduled job ( Shedlock ):
```sql
-- Auto-close previous month on the 5th
UPDATE accounting_periods
SET status = 'CLOSED', closed_at = NOW()
WHERE status = 'OPEN'
  AND (year, month) < (EXTRACT(YEAR FROM CURRENT_DATE), EXTRACT(MONTH FROM CURRENT_DATE))
  AND CURRENT_DATE >= DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '4 days';
```

#### G2. Period Validation on Journal Entry
```java
@BeforePersist
void validatePeriodOpen(JournalEntry entry) {
    AccountingPeriod period = entry.getPeriod();
    if (period.getStatus() != PeriodStatus.OPEN) {
        throw new AccountingException("Cannot post to closed period: " + period.getName());
    }
}
```

---

### H. COMMISSION AUTOMATION

| Event | Action |
|-------|--------|
| Order DELIVERED | Calculate commission, create Commission record |
| Commission APPROVED | Update agent balance |
| Payout CREATED | Link commissions, update status=PROCESSING |
| Payout PAID | Update status=PAID, update agent total_commission |

**Trigger:** Event listeners on OrderStatusChangedEvent, CommissionStatusChangedEvent

---

### I. PAYMENT AUTOMATION

| Event | Actions |
|-------|---------|
| Payment SUCCESS | Allocate to orders, update customer ledger, update outstanding |
| Payment VOIDED | Reverse allocation, update customer ledger |
| Partial Payment | Proportional allocation |

---

### J. PURCHASE ORDER AUTOMATION

| Event | Actions |
|-------|---------|
| PO Received | Create inventory_layers, update supplier ledger, update product cost_price |
| PO Return | Reduce inventory, update supplier ledger (debit) |
| PO Payment | Update supplier ledger, update payment status |

---

## SERVICE DEPENDENCIY GRAPH

```
OrderServiceImpl
  ├── InventoryService (stock deduction)
  ├── AuditLogService (manual - to be replaced with events)
  ├── CommissionLifecycleService
  ├── CustomerService
  └── OrderPaymentService
       ├── PaymentService
       └── CustomerLedgerService --> Creates customer_ledger entries

PurchaseOrderServiceImpl
  ├── InventoryService (stock addition)
  ├── SupplierLedgerService --> Creates supplier_ledger entries
  └── AuditLogService (manual - to be replaced with events)

CommissionLifecycleService
  └── AgentLedgerService --> Creates agent_ledger entries

ExpenseService
  └── SupplierLedgerService (if supplier billed)
```

---

## MIGRATIONS SUMMARY (Updated)

| Migration | Description |
|-----------|-------------|
| **V91** | Add pessimistic locking queries to InventoryRepository |
| **V92** | Add CHECK constraint `chk_order_items_product_or_combo` |
| **V93** | Create fiscal_years, accounting_periods, accounting_period_audit tables |
| **V94** | Create DB triggers: prevent posting to closed periods |
| **V95** | Create stock_transfers, stock_transfer_items tables |
| **V96** | Create customer_ledger, supplier_ledger, supplier_balance_cache tables |
| **V97** | Create Chart of Accounts, Accounts, Journal Entries, General Ledger, Account Balances |
| **V98** | Add partial indexes for soft delete |
| **V99** | Audit table partitioning strategy |
| **V100** | Add auto_close_day to whitelist_config |

---

## CODE CHANGES REQUIRED (Summary)

| Component | Change |
|-----------|--------|
| **InventoryRepository** | Add `findByProductIdWithPessimisticLock()` with `@Lock(PESSIMISTIC_WRITE)` |
| **AuditLogService** | Refactor to event listener, remove manual calls from services |
| **CustomerLedgerService** | New service - handles all customer ledger entries |
| **SupplierLedgerService** | New service - handles all supplier ledger entries |
| **JournalEntryService** | New service - GL journal entries |
| **AccountingPeriodService** | New service - period management + auto-close |
| **StockTransferService** | New service - inter-warehouse transfers |
| **All Services** | Remove manual `auditLogService.record()` calls |
| **JPA Entities** | Add `@EntityListeners(AuditListener.class)` |

---

## VERIFICATION CHECKLIST

After implementation, verify:

- [ ] Order creation → Customer ledger entry created automatically
- [ ] Payment received → Customer ledger + Journal entry created automatically
- [ ] PO received → Supplier ledger + Inventory layer + Journal entry created automatically
- [ ] Supplier payment → Supplier ledger + Journal entry created automatically
- [ ] Stock transfer → Two inventory transactions created automatically
- [ ] Closed period → Journal entry rejected with error
- [ ] Concurrent orders → No negative stock (SELECT FOR UPDATE working)
- [ ] All entities → Audit entries created on CREATE/UPDATE/DELETE
