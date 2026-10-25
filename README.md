# TxScope

**Open-source diagnostic tool that explains *why* a Spring Boot / HikariCP / PostgreSQL application has no more DB connections — with facts, not guesses.**

When the connection pool is saturated, metrics only show the symptom (`active = max`, `pending > 0`).  
Finding the real cause still requires manual correlation of pool state, Spring transactions, and PostgreSQL activity.  
TxScope automates that correlation and produces a clear local report.

> **Status:** Early development — demo application + Scenario S1 available.  
> Collectors, hypothesis engine and HTML/JSON report are next.

---

## The problem

Common production failure modes:

| What you see | Typical root cause |
|--------------|--------------------|
| `Connection is not available, request timed out` | Connection held across non-DB work |
| Hikari `active = max`, `pending > 0` | Long-running transaction or lock |
| Application slow / unavailable | Pool exhausted while PostgreSQL looks healthy |

Teams still piece the diagnosis together from Actuator metrics, logs, and `pg_stat_activity`. Under incident pressure this is slow and error-prone.

## What TxScope does

TxScope attaches to a Spring Boot + HikariCP + PostgreSQL application and produces, on demand, a **local HTML + JSON report** containing:

1. **Pool snapshot** — active, idle, pending, max, timeouts, hold-time samples  
2. **Long-held transactions / connections** — duration and application context when available  
3. **PostgreSQL activity** — sessions, `wait_event`, blocking PIDs (read-only access)  
4. **Facts** — only what was observed  
5. **Hypotheses** — explicitly labelled, with confidence level and concrete next steps  
6. **Insufficient evidence** — when data does not allow a conclusion  

**Principles**

- Facts before narrative  
- Runs on the user’s infrastructure (no mandatory telemetry)  
- Never collects SQL bind parameters  
- Database privileges kept read-only as much as possible  
- Reproducible failure scenarios to validate the tool  

This is **not** a general-purpose APM and not a Datadog competitor.  
It is a focused developer tool for the classic “pool is full” incident.

---

## Quick start — reproduce the pain (current state)

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker

### 1. Start PostgreSQL

```bash
docker compose up -d
```

### 2. Run the demo application

```bash
mvn spring-boot:run -pl demo-app
```

The pool is intentionally small (`maximum-pool-size: 5`) so saturation is easy to trigger.

### 3. Trigger Scenario S1

**S1** = external work (HTTP / sleep) performed *inside* a `@Transactional` method.  
The connection is held for the whole duration even though almost no SQL is running.

```bash
# Single request (holds a connection for 3 seconds)
curl "http://localhost:8080/scenarios/s1?delayMs=3000"

# Concurrent load → pool saturation
./scripts/load-s1.sh
```

### 4. Observe the symptoms manually

```bash
# Hikari metrics
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.active
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.pending

# PostgreSQL — most sessions should be idle or idle in transaction
docker exec -it txscope-postgres psql -U txscope -d txscope \
  -c "SELECT pid, state, wait_event_type, wait_event, query_start
      FROM pg_stat_activity
      WHERE datname = 'txscope';"
```

You should see:

- Hikari `active` at maximum, `pending` > 0, possible timeouts  
- PostgreSQL mostly healthy / idle-in-transaction (the expensive work is outside SQL)

This is exactly the situation TxScope will later diagnose automatically.

---

## Project structure

```
txscope/
├── demo-app/                      # Spring Boot app with intentional failure scenarios
├── txscope-core/                  # Model, collectors, correlation & hypothesis engines
├── txscope-spring-boot-starter/   # Auto-configuration + report endpoint
├── scripts/                       # Load scripts for the reference scenarios
├── docs/                          # Architecture & scenario documentation
├── docker-compose.yml
└── pom.xml                        # Multi-module parent
```

---

## Reference scenarios

| ID | Scenario | Status |
|----|----------|--------|
| **S1** | External HTTP / blocking call inside `@Transactional` | Available |
| **S2** | PostgreSQL lock contention | Planned |
| **S3** | Slow SQL | Planned |
| **S4** | Connection not returned (simulated leak) | Planned |

Success criterion: a developer who only has the TxScope report identifies the contributing factor faster than with metrics + logs alone, with an acceptable false-hypothesis rate.

---

## Roadmap

- [x] Multi-module Maven structure  
- [x] Demo application + Scenario S1  
- [ ] Scenarios S2, S3, S4  
- [ ] Pool, transaction and PostgreSQL collectors  
- [ ] Correlation engine (facts only)  
- [ ] Hypothesis engine with confidence levels  
- [ ] JSON + HTML report  
- [ ] Spring Boot starter (one dependency)  
- [ ] 10-minute end-to-end README path  
- [ ] `v0.1.0` public release  

---

## Non-goals (MVP)

- General APM platform  
- Multi-tenant SaaS  
- MySQL / Oracle / Quarkus support (later)  
- Automatic remediation (killing sessions in production)  
- Collection of SQL bind parameters  

---

## Contributing

Contributions are welcome once the first public milestone is tagged.  
Please open an issue before starting significant work so we can keep the scope focused.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).
