#!/usr/bin/env bash
# Scenario S4 – connection leak
# Leak connections one by one until the pool is exhausted

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
LEAKS="${LEAKS:-6}"   # pool size is 5 → 6th should fail or pending

echo "=== TxScope S4 load (connection leak) ==="
echo "Leaking $LEAKS connections..."
echo ""

for i in $(seq 1 "$LEAKS"); do
  code=$(curl -s -o /tmp/s4-body.json -w "%{http_code}" \
    -X POST \
    --max-time 10 \
    "$BASE_URL/scenarios/s4/leak" || echo "000")
  body=$(cat /tmp/s4-body.json 2>/dev/null || true)
  echo "leak $i → HTTP $code  $body"
done

echo ""
echo "Status:"
curl -s "$BASE_URL/scenarios/s4/status"
echo ""
echo "Pool should be exhausted. Check Hikari metrics and leak detection logs."
echo "Cleanup later: curl -X POST $BASE_URL/scenarios/s4/release"
