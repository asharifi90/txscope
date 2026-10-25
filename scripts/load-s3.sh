#!/usr/bin/env bash
# Scenario S3 – slow SQL (pg_sleep)
# Concurrent long queries → connections busy in PostgreSQL

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
CONCURRENCY="${CONCURRENCY:-8}"
REQUESTS="${REQUESTS:-12}"
SECONDS_SLEEP="${SECONDS_SLEEP:-4}"

echo "=== TxScope S3 load (slow SQL) ==="
echo "URL: $BASE_URL/scenarios/s3?seconds=$SECONDS_SLEEP"
echo "Concurrency: $CONCURRENCY  Requests: $REQUESTS"
echo ""

for i in $(seq 1 "$REQUESTS"); do
  (
    code=$(curl -s -o /dev/null -w "%{http_code}" \
      --max-time 30 \
      "$BASE_URL/scenarios/s3?seconds=$SECONDS_SLEEP" || echo "000")
    echo "request $i → HTTP $code"
  ) &
  if (( i % CONCURRENCY == 0 )); then
    wait
  fi
done
wait
echo "Done. Check active connections in pg_stat_activity (state=active)."
