# TxScope

**Explains *why* a Spring Boot / HikariCP / PostgreSQL app has no more DB connections — with facts, not guesses.**

When the pool is saturated, metrics only show the symptom (`active = max`, `pending > 0`).  
TxScope correlates pool state, Spring transactions, and PostgreSQL activity into a local JSON report with labelled hypotheses.

> **Status:** Early development. Demo scenarios S1–S4 and JSON diagnostics work locally.  
> Not a production-hardened release.

---

## Quick start

```bash
docker compose up -d
mvn clean install -DskipTests
mvn spring-boot:run -pl demo-app
```

```bash
# Example: S1 load, then report while load is still running
./scripts/load-s1.sh

curl -s http://localhost:8080/internal/txscope/report | jq
```

Report endpoint: `GET /internal/txscope/report`

---

## Failure modes (demo)

| ID | Scenario | What happens | Hypothesis |
|----|----------|--------------|------------|
| **S1** | Work inside `@Transactional` | Connection held during sleep / HTTP-like delay | **H1** — held across non-DB work |
| **S2** | Lock contention | One session holds a lock; others wait | **H2** — lock contention |
| **S3** | Slow SQL | Connections busy in `pg_sleep` / long queries | **H3** — slow SQL in PostgreSQL |
| **S4** | Connection leak | Connections borrowed and never returned | **H4** — possible leak |

| Endpoint | Role |
|----------|------|
| `GET /scenarios/s1?delayMs=3000` | S1 |
| `GET /scenarios/s2/hold` · `GET /scenarios/s2/wait` | S2 |
| `GET /scenarios/s3?seconds=5` | S3 |
| `POST /scenarios/s4/leak` | S4 |
| `GET /internal/txscope/report` | Diagnostic JSON |

---

## How diagnostics stay available when the app pool is full

| Pool | Role |
|------|------|
| **Application Hikari pool** | Business traffic (`TxScopeHikariPool`, size 5 in the demo) |
| **Monitor pool** | Size **1**, used only to sample `pg_stat_activity` / lock waits |

If the app pool is exhausted, TxScope does **not** rely on it for PostgreSQL evidence. The monitor connection has short timeouts so diagnostics fail fast instead of hanging.

---

## Report shape

| Section | Content |
|---------|---------|
| **Pool** | Application pool: active / idle / pending / max |
| **Facts** | Observed only (pool, transactions, postgres) |
| **Hypotheses** | `H1`–`H4` (or `H0`), confidence, next steps |
| **Insufficient evidence** | e.g. monitor timeout or collection failure (not silent “empty DB”) |

---

## Modules

| Module | Role |
|--------|------|
| `txscope-core` | Collectors, model, hypothesis engine |
| `txscope-spring-boot-starter` | Auto-config + report endpoint |
| `demo-app` | Runnable demo (S1–S4) |

---

## Limits (honest)

- Early MVP: demo wiring may use explicit config; harden starter auto-config over time.
- H2 can still use demo transaction names as a secondary signal when useful.
- Report endpoint is **internal**, protect or disable it in real deployments.
- No HTML UI yet; JSON only.
- Not a full APM — focused on connection-pool saturation diagnosis.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).
