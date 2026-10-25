#!/usr/bin/env bash
# Load script for Scenario S1 – saturate the Hikari pool with concurrent
# requests that hold a connection while doing "external" work.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
CONCURRENCY="${CONCURRENCY:-10}"
DELAY_MS="${DELAY_MS:-4000}"
REQUESTS="${REQUESTS:-30}"

echo "=== TxScope S1 load ==="
echo "URL          : $BASE_URL/scenarios/s1?delayMs=$DELAY_MS"
echo "Concurrency  : $CONCURRENCY"
echo "Total requests: $REQUESTS"
echo ""

# Simple parallel load with curl + background jobs
for i in $(seq 1 "$REQUESTS"); do
  (
    code=$(curl -s -o /dev/null -w "%{http_code}" \
      --max-time 15 \
      "$BASE_URL/scenarios/s1?delayMs=$DELAY_MS" || echo "000")
    echo "request $i → HTTP $code"
  ) &

  # Limit concurrency
  if (( i % CONCURRENCY == 0 )); then
    wait
  fi
done
wait

echo ""
echo "Done. Check:"
echo "  - Application logs (Hikari timeout / pending threads)"
echo "  - Actuator metrics: curl $BASE_URL/actuator/metrics/hikaricp.connections.active"
echo "  - PostgreSQL: SELECT * FROM pg_stat_activity WHERE datname = 'txscope';"
