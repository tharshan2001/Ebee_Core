# Print / PDF Generation API Documentation

## Overview

The Print module generates PDF documents (invoices, receipts, labels, payout statements) on-the-fly using Thymeleaf templates rendered to PDF via Flying Saucer (iText).

All templates live in `src/main/resources/templates/print/`.

## Base URL
```
http://localhost:8085
```

## Authentication

All print endpoints require JWT authentication via Authorization header:
```
Authorization: Bearer <JWT_TOKEN>
```

### Roles
| Endpoint | Required Role |
|----------|---------------|
| Invoice / Receipt / Shipping Label | ADMIN or AGENT |
| Product Label | PUBLIC (no auth) |
| Payout Statement | ADMIN |

---

## Endpoints

### 1. Download Invoice
**GET** `/api/orders/{id}/invoice`

Generates a PDF invoice for the given order.

**Access:** ADMIN, AGENT

**Path Parameters:**
| Name | Type | Description |
|------|------|-------------|
| id | UUID | Order ID |

**Response:** PDF file (`invoice-{id}.pdf`)

**Response Headers:**
```
Content-Type: application/pdf
Content-Disposition: attachment; filename=invoice-{id}.pdf
```

**Template:** `templates/print/invoice.html`

**Data:**
| Variable | Type | Source |
|----------|------|--------|
| invoice | InvoiceData | Order data mapped to invoice fields |
| lineItems | List<InvoiceLineItem> | Order items with product name, qty, price |

---

### 2. Download Receipt
**GET** `/api/orders/{id}/receipt`

Generates a PDF receipt confirming payment for the given order.

**Access:** ADMIN, AGENT

**Path Parameters:**
| Name | Type | Description |
|------|------|-------------|
| id | UUID | Order ID |

**Response:** PDF file (`receipt-{id}.pdf`)

**Template:** `templates/print/receipt.html`

**Data:**
| Variable | Type | Source |
|----------|------|--------|
| receipt | ReceiptData | Order data (order number, date, amounts) |

---

### 3. Download Shipping Label
**GET** `/api/orders/{id}/shipping-label`

Generates a PDF shipping label with delivery address and order info.

**Access:** ADMIN, AGENT

**Path Parameters:**
| Name | Type | Description |
|------|------|-------------|
| id | UUID | Order ID |

**Response:** PDF file (`shipping-label-{id}.pdf`)

**Template:** `templates/print/shipping-label.html`

**Data:**
| Variable | Type | Source |
|----------|------|--------|
| label | ShippingLabelData | Customer name, address, phone, order number |

---

### 4. Download Product Label
**GET** `/api/products/{id}/label`

Generates a PDF product label (100mm x 60mm) with product details.

**Access:** PUBLIC

**Path Parameters:**
| Name | Type | Description |
|------|------|-------------|
| id | UUID | Product ID |

**Response:** PDF file (`product-label-{id}.pdf`)

**Template:** `templates/print/product-label.html`

**Data:**
| Variable | Type | Source |
|----------|------|--------|
| label | LabelData | Product name, SKU, price |

---

### 5. Download Payout Statement
**GET** `/api/payouts/{id}/pdf`

Generates a PDF payout statement for an agent commission payout.

**Access:** ADMIN

**Path Parameters:**
| Name | Type | Description |
|------|------|-------------|
| id | UUID | Payout ID |

**Response:** PDF file (`payout-{id}.pdf`)

**Template:** `templates/print/payout-statement.html`

**Data:**
| Variable | Type | Source |
|----------|------|--------|
| payout | PayoutData | Payout record (agent name, amount, period) |

---

## Configuration

Company information used in all templates is configured via `app.print` properties in `application.yml`:

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `app.print.company-name` | `PRINT_COMPANY_NAME` | `Ebee (Pvt) Ltd` | Legal company name |
| `app.print.company-address` | `PRINT_COMPANY_ADDRESS` | (empty) | Registered address |
| `app.print.company-phone` | `PRINT_COMPANY_PHONE` | (empty) | Contact phone |
| `app.print.company-email` | `PRINT_COMPANY_EMAIL` | (empty) | Contact email |
| `app.print.company-registration` | `PRINT_COMPANY_REGISTRATION` | (empty) | Business reg. number |
| `app.print.logo-url` | `PRINT_LOGO_URL` | (empty) | Logo URL for templates |

## Architecture

```
Controller (OrderController / ProductController)
  → Service (OrderService / ProductService)
    → PrintTemplateService.renderToPdf(template, variables)
      → Thymeleaf TemplateEngine (TemplateMode.HTML)
        → ITextRenderer (Flying Saucer)
          → byte[] PDF
```

`PrintTemplateService` is a dedicated `@Service` that:
- Configures a standalone `ClassLoaderTemplateResolver` pointed at `templates/print/`
- Injects `PrintProperties` for company info
- Exposes `renderToPdf(templateName, variables)` returning `byte[]`

All service-layer methods pass order/product data as `Map<String, Object>` via the shared `toMap()` helper.

## Templates

| File | Page Size | Use |
|------|-----------|-----|
| `invoice.html` | A4 | Full invoice with line items |
| `receipt.html` | A4 | Payment receipt |
| `shipping-label.html` | 100mm x 150mm | Shipping address label |
| `product-label.html` | 100mm x 60mm | Product shelf/barcode label |
| `payout-statement.html` | A4 | Agent commission statement |

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `PRINT_COMPANY_NAME` | No | `Ebee (Pvt) Ltd` | Company name on documents |
| `PRINT_COMPANY_ADDRESS` | No | (empty) | Address line(s) |
| `PRINT_COMPANY_PHONE` | No | (empty) | Phone number |
| `PRINT_COMPANY_EMAIL` | No | (empty) | Email address |
| `PRINT_COMPANY_REGISTRATION` | No | (empty) | Business registration |
| `PRINT_LOGO_URL` | No | (empty) | URL to company logo |
