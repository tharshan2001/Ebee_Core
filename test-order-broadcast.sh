#!/bin/bash
# Test order status broadcast via WebSocket
# 1. Login → get JWT
# 2. Fetch orders → pick one
# 3. Change status → triggers broadcast to /topic/orders

API="http://localhost:8085"

echo "=== Step 1: Login ==="
LOGIN_RESP=$(curl -s -X POST "$API/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"super@ebee.lk","password":"Super@dmin123!"}')

TOKEN=$(echo "$LOGIN_RESP" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "Login failed. Response:"
  echo "$LOGIN_RESP" | python3 -m json.tool 2>/dev/null || echo "$LOGIN_RESP"
  exit 1
fi
echo "Token: ${TOKEN:0:20}..."

echo ""
echo "=== Step 2: Fetch pending orders ==="
ORDERS_RESP=$(curl -s "$API/api/orders?page=0&size=5&status=PENDING" \
  -H "Authorization: Bearer $TOKEN")

echo "$ORDERS_RESP" | python3 -m json.tool 2>/dev/null | head -40

ORDER_ID=$(echo "$ORDERS_RESP" | python3 -c "
import sys, json
data = json.load(sys.stdin)
content = data.get('data', {}).get('content', [])
if content:
    print(content[0]['id'])
else:
    print('')
" 2>/dev/null)

if [ -z "$ORDER_ID" ]; then
  echo ""
  echo "No pending orders found. Trying any order..."
  ORDERS_RESP=$(curl -s "$API/api/orders?page=0&size=5" \
    -H "Authorization: Bearer $TOKEN")
  ORDER_ID=$(echo "$ORDERS_RESP" | python3 -c "
import sys, json
data = json.load(sys.stdin)
content = data.get('data', {}).get('content', [])
for o in content:
    if o.get('status') not in ('DELIVERED', 'CANCELLED', 'RETURNED'):
        print(o['id'])
        break
" 2>/dev/null)
fi

if [ -z "$ORDER_ID" ]; then
  echo "No suitable order found to test with."
  exit 1
fi
echo ""
echo "Order to update: $ORDER_ID"

echo ""
echo "=== Step 3: Change status → CONFIRMED (triggers WebSocket broadcast) ==="
STATUS_RESP=$(curl -s -X PATCH "$API/api/orders/$ORDER_ID/status?status=CONFIRMED" \
  -H "Authorization: Bearer $TOKEN")
echo "$STATUS_RESP" | python3 -m json.tool 2>/dev/null

echo ""
echo "=== Step 4: Verify — the server should have broadcast to /topic/orders ==="
echo "Check the server logs for: 'Broadcast order status change: order=... PENDING → CONFIRMED'"
echo ""
echo "Done. If WebSocket clients are connected, they will receive the update in real-time."
