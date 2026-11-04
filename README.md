# TxScope

**Explains *why* a Spring Boot / HikariCP / PostgreSQL application has no more database connections, with facts, not guesses.**

Pool metrics only show the symptom (`active = max`, `pending > 0`).  
TxScope correlates **Hikari pool state**, **open Spring transactions**, and **PostgreSQL session activity** into an on-demand JSON report with labelled hypotheses and explicit collection status.

> **Status:** Early public MVP. Scenarios S1–S4, monitor pool, JSON report, unit tests, and Testcontainers acceptance tests are in place.  
> **Not** a production-hardened or Maven Central release.

---

## Quick start (demo)

**Requirements:** Java 21+, Maven 3.9+, Docker

```bash
docker compose up -d
mvn clean install
mvn spring-boot:run -pl demo-app
```

```bash
# Load a scenario (example: S1), then call the report *while* load is still running
./scripts/load-s1.sh

curl -s http://localhost:8080/internal/txscope/report | jq
```

In the demo app, the report API is enabled via:

```yaml
txscope:
  report-enabled: true   # default is false (safe default for real apps)
```

---

## Failure modes

| ID | Scenario | What you trigger | Expected hypothesis |
|----|----------|------------------|---------------------|
| **S1** | External work inside `@Transactional` | Sleep / delay while a transaction holds a connection | **H1** — held across non-DB work |
| **S2** | Lock contention | One session holds a lock; others wait | **H2** — lock contention |
| **S3** | Slow SQL | Long `pg_sleep` / active queries | **H3** — slow SQL in PostgreSQL |
| **S4** | Connection leak | Connections borrowed and never returned | **H4** — possible leak |

| Endpoint | Role |
|----------|------|
| `GET /scenarios/s1?delayMs=3000` | S1 |
| `GET /scenarios/s2/hold` · `GET /scenarios/s2/wait` | S2 |
| `GET /scenarios/s3?seconds=5` | S3 |
| `POST /scenarios/s4/leak` · `POST /scenarios/s4/release` | S4 |
| `GET /internal/txscope/report` | Diagnostic JSON (only if `txscope.report-enabled=true`) |

---

## Design: two pools

| Pool | Role |
|------|------|
| **Application Hikari pool** | Business traffic (demo: `TxScopeHikariPool`, max 5) |
| **Monitor pool** | Size **1**, short timeouts — samples `pg_stat_activity` / lock waits only |

When the application pool is exhausted, PostgreSQL evidence is still collected through the **monitor** connection (or reported as `TIMEOUT` / `FAILED` under `collection` / `insufficientEvidence` — never silently treated as an empty database).

---

## Report shape

| Field | Meaning |
|-------|---------|
| `pool` | Application pool snapshot (active / idle / pending / max) |
| `collection` | Per-source status: `OK`, `EMPTY`, `TIMEOUT`, `FAILED`, `UNAVAILABLE` |
| `facts` | Observed signals only |
| `hypotheses` | `H0`–`H4`, confidence, supporting fact ids, next steps |
| `insufficientEvidence` | Missing or failed measurements |

---

## Modules

| Module | Role |
|--------|------|
| `txscope-core` | Model, collectors, hypothesis engine |
| `txscope-spring-boot-starter` | Auto-configuration, optional report endpoint |
| `demo-app` | Runnable failure scenarios S1–S4 |

---

## Use as a library (local install)

Not published to Maven Central yet. From this repository:

```bash
mvn clean install
```

```xml
<dependency>
  <groupId>com.txscope</groupId>
  <artifactId>txscope-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
txscope:
  report-enabled: true
  # monitor.jdbc-url / username / password optional (default: spring.datasource.*)
```

Requires a `DataSource` (HikariCP + PostgreSQL recommended). See [SECURITY.md](SECURITY.md).

---

## Tests & CI

- **Unit tests** — hypothesis rules (H1–H4) in `txscope-core`
- **Acceptance tests** — S1–S4 with Testcontainers (PostgreSQL) in `demo-app`
- **CI** — GitHub Actions runs `mvn test` on push/PR

```bash
mvn -B clean test
```

---

## Limits (honest)

- Starter auto-config is still maturing; the demo may use explicit configuration (`TxScopeDemoConfig`).
- H2 may still use demo transaction names as a **secondary** signal when lock rows are sparse.
- Report endpoint must stay **off** or protected in real deployments (`txscope.report-enabled`, network policy, auth).
- JSON only (no HTML UI).
- Focused on **pool saturation diagnosis** — not a general APM.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).
