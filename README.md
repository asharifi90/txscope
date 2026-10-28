# TxScope

**Explains *why* a Spring Boot / HikariCP / PostgreSQL app has no more DB connections — with facts, not guesses.**

When the pool is saturated, metrics only show the symptom (`active = max`, `pending > 0`).  
TxScope correlates pool state, Spring transactions, and PostgreSQL activity into a local JSON report with labelled hypotheses.

> **Status:** Early development (MVP diagnostics work for S1–S4). Not a production-hardened release.

---

## Quick start

```bash
docker compose up -d
mvn clean install -DskipTests
mvn spring-boot:run -pl demo-app
```

```bash
# Saturate with scenario S1, then (while load runs):
./scripts/load-s1.sh

curl -s http://localhost:8080/internal/txscope/report | jq
```

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

## Report shape

| Section | Content |
|---------|---------|
| **Pool** | active / idle / pending / max |
| **Facts** | Observed only (pool, transactions, postgres) |
| **Hypotheses** | `H1`–`H4` (or `H0`), confidence, next steps |
| **Insufficient evidence** | e.g. Postgres not sampled when the app pool is full |

---

## Modules

| Module | Role |
|--------|------|
| `txscope-core` | Collectors, model, hypothesis engine |
| `txscope-spring-boot-starter` | Auto-config + report endpoint |
| `demo-app` | Runnable demo (S1–S4) |

---

## Limits (honest)

- Postgres sampling uses the **same** app pool → often empty when the pool is saturated (see `insufficientEvidence`).
- H2 may use demo transaction names when lock-wait rows are missing.
- Report endpoint is **internal** — protect it in real deployments.
- No HTML UI yet.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).
