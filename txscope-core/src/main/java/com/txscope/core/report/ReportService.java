package com.txscope.core.report;

import com.txscope.core.collector.pool.PoolSnapshotCollector;
import com.txscope.core.collector.postgres.PostgresActivityCollector;
import com.txscope.core.collector.transaction.TransactionTracker;
import com.txscope.core.model.Hypothesis;
import com.txscope.core.model.pool.DiagnosticReport;
import com.txscope.core.model.pool.Fact;
import com.txscope.core.model.pool.PoolSnapshot;
import com.txscope.core.model.postgres.PgSession;
import com.txscope.core.model.transaction.OpenTransaction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReportService {

    private final PoolSnapshotCollector poolCollector;
    private final TransactionTracker transactionTracker;
    private final PostgresActivityCollector postgresCollector;
    private final HypothesisEngine hypothesisEngine;

    public ReportService(
            PoolSnapshotCollector poolCollector,
            TransactionTracker transactionTracker,
            PostgresActivityCollector postgresCollector,
            HypothesisEngine hypothesisEngine) {
        this.poolCollector = poolCollector;
        this.transactionTracker = transactionTracker;
        this.postgresCollector = postgresCollector;
        this.hypothesisEngine = hypothesisEngine;
    }

    public DiagnosticReport generateReport() {
        Instant now = Instant.now();
        DiagnosticReport.Builder builder = DiagnosticReport.builder().generatedAt(now);
        List<Fact> allFacts = new ArrayList<>();
        PoolSnapshot pool = null;

        Optional<PoolSnapshot> poolOpt = poolCollector.collect();
        if (poolOpt.isEmpty()) {
            builder.addInsufficientEvidence(
                    "Pool snapshot unavailable (DataSource is not HikariCP or pool not started)");
        } else {
            pool = poolOpt.get();
            builder.pool(pool);
            List<Fact> poolFacts = factsFromPool(pool);
            allFacts.addAll(poolFacts);
            if (pool.getActive() == 0 && pool.getPending() == 0) {
                builder.addInsufficientEvidence(
                        "Pool is idle — run a scenario load script then request the report again");
            }
        }

        List<OpenTransaction> openTxs = transactionTracker.snapshot();
        allFacts.addAll(factsFromTransactions(openTxs));

        List<PgSession> sessions = postgresCollector.collect();
        allFacts.addAll(factsFromPostgres(sessions));

        for (Fact fact : allFacts) {
            builder.addFact(fact);
        }

        List<Hypothesis> hypotheses = hypothesisEngine.evaluate(pool, allFacts);
        builder.hypotheses(hypotheses);

        return builder.build();
    }

    private List<Fact> factsFromPool(PoolSnapshot pool) {
        List<Fact> facts = new ArrayList<>();
        int n = 1;

        if (pool.isFullyActive()) {
            facts.add(new Fact(
                    "F" + n++,
                    "All pool connections are active (" + pool.getActive() + "/" + pool.getMax() + ")",
                    "pool"
            ));
        }
        if (pool.hasWaiters()) {
            facts.add(new Fact(
                    "F" + n++,
                    "Threads are waiting for a connection (pending=" + pool.getPending()
                            + ", threadsAwaiting=" + pool.getThreadsAwaitingConnection() + ")",
                    "pool"
            ));
        }
        if (pool.getIdle() == 0 && pool.getActive() > 0) {
            facts.add(new Fact(
                    "F" + n++,
                    "No idle connections available (idle=0, active=" + pool.getActive() + ")",
                    "pool"
            ));
        }
        if (facts.isEmpty()) {
            facts.add(new Fact(
                    "F1",
                    "Pool state: active=" + pool.getActive()
                            + " idle=" + pool.getIdle()
                            + " pending=" + pool.getPending()
                            + " max=" + pool.getMax(),
                    "pool"
            ));
        }
        return facts;
    }

    private List<Fact> factsFromTransactions(List<OpenTransaction> openTxs) {
        List<Fact> facts = new ArrayList<>();
        if (openTxs.isEmpty()) {
            return facts;
        }
        facts.add(new Fact(
                "T1",
                openTxs.size() + " Spring transaction(s) currently open",
                "transaction"
        ));
        int i = 2;
        for (OpenTransaction tx : openTxs) {
            if (tx.getDurationMs() >= 1000) {
                facts.add(new Fact(
                        "T" + i++,
                        "Long-running transaction name=" + tx.getName()
                                + " durationMs=" + tx.getDurationMs(),
                        "transaction"
                ));
            }
        }
        return facts;
    }

    private List<Fact> factsFromPostgres(List<PgSession> sessions) {
        List<Fact> facts = new ArrayList<>();
        if (sessions.isEmpty()) {
            facts.add(new Fact("P0", "No other PostgreSQL sessions found for this database", "postgres"));
            return facts;
        }

        long idleInTx = sessions.stream()
                .filter(s -> s.getState() != null && s.getState().contains("idle in transaction"))
                .count();
        long active = sessions.stream()
                .filter(s -> "active".equalsIgnoreCase(s.getState()))
                .count();
        long blocked = sessions.stream().filter(PgSession::isBlocked).count();

        facts.add(new Fact(
                "P1",
                "PostgreSQL sessions: total=" + sessions.size()
                        + " active=" + active
                        + " idle_in_transaction=" + idleInTx
                        + " blocked=" + blocked,
                "postgres"
        ));

        int i = 2;
        for (PgSession s : sessions) {
            if (s.getState() != null && s.getState().contains("idle in transaction")
                    && s.getSecondsInState() != null && s.getSecondsInState() >= 1) {
                facts.add(new Fact(
                        "P" + i++,
                        "Session pid=" + s.getPid()
                                + " state=idle in transaction for " + s.getSecondsInState() + "s",
                        "postgres"
                ));
            }
            if (s.isBlocked()) {
                facts.add(new Fact(
                        "P" + i++,
                        "Session pid=" + s.getPid()
                                + " is blocked (wait_event="
                                + nullToDash(s.getWaitEventType()) + "/"
                                + nullToDash(s.getWaitEvent()) + ")",
                        "postgres"
                ));
            }
            if ("active".equalsIgnoreCase(s.getState())
                    && s.getSecondsInState() != null && s.getSecondsInState() >= 2) {
                facts.add(new Fact(
                        "P" + i++,
                        "Session pid=" + s.getPid()
                                + " active for " + s.getSecondsInState() + "s"
                                + " wait_event=" + nullToDash(s.getWaitEvent()),
                        "postgres"
                ));
            }
        }
        return facts;
    }

    private static String nullToDash(String v) {
        return v != null ? v : "-";
    }
}