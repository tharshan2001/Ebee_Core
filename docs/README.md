# Ebee API Documentation

## Modules

### Order API
- [Order API Documentation](ORDER_API.md)
- Complete endpoint reference, request/response formats, examples

### Customer API
- [Customer API Documentation](CUSTOMER_API.md)
- Create, list, and lookup customers by ID or phone number

### Commission API
- [Commission API Documentation](COMMISSION_API.md)
- Commission lifecycle, config management, state machine details

### Product & Agent API
- [Product & Agent Quick Reference](PRODUCT_AGENT_API.md)
- Full CRUD for agents with district-based auto-code generation
- Required for order creation (products, agents, authentication)

### Agent Application API
- [Agent Application API Documentation](AGENT_APPLICATION_API.md)
- Public submission endpoint, admin list/view/delete

---

## Quick Start

### 1. Login
```bash
curl -X POST http://localhost:8085/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@ebee.com","password":"admin123"}'
```

### 2. Get Products & Agents
```bash
# Get products
curl http://localhost:8085/api/products/active \
  -H "Authorization: Bearer <TOKEN>"

# Get agents  
curl http://localhost:8085/api/agents \
  -H "Authorization: Bearer <TOKEN>"
```

### 3. Create Order
```bash
curl -X POST http://localhost:8085/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{
    "agentCode": "AG-001",
    "customerPhoneNumber": "+94771234567",
    "items": [{"productSku": "PA-001", "quantity": 2}]
  }'
```

### 4. Update Order Status (Admin)
```bash
curl -X PATCH "http://localhost:8085/api/orders/<ORDER_ID>/status?status=CONFIRMED" \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

---

## Base URL
```
http://localhost:8085/api
```

## Authentication
All endpoints require JWT token in header:
```
Authorization: Bearer <JWT_TOKEN>
```