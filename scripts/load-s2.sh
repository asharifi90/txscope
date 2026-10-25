#!/usr/bin/env bash
# Scenario S2 – lock contention
# 1) Start one holder that keeps the advisory lock
# 2) Fire several waiters that block on the same lock

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
HOLD_MS="${HOLD_MS:-15000}"
WAITERS="${WAITERS:-8}"

echo "=== TxScope S2 load (PostgreSQL lock) ==="
echo "1) Starting lock holder for ${HOLD_MS} ms..."
curl -s -o /dev/null -w "holder → HTTP %{http_code}\n" \
  --max-time $((HOLD_MS / 1000 + 5)) \
  "$BASE_URL/scenarios/s2/hold?holdMs=$HOLD_MS" &
HOLDER_PID=$!

sleep 1
echo "2) Starting $WAITERS waiters..."
for i in $(seq 1 "$WAITERS"); do
  (
    code=$(curl -s -o /dev/null -w "%{http_code}" \
      --max-time 20 \
      "$BASE_URL/scenarios/s2/wait?timeoutHintMs=5000" || echo "000")
    echo "waiter $i → HTTP $code"
  ) &
done

wait $HOLDER_PID || true
wait
echo "Done. Check pool metrics + pg_stat_activity / pg_locks."
