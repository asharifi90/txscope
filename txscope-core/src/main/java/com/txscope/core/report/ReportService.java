package com.txscope.core.report;


import com.txscope.core.collector.pool.PoolSnapshotCollector;
import com.txscope.core.collector.transaction.TransactionTracker;
import com.txscope.core.model.pool.DiagnosticReport;
import com.txscope.core.model.pool.Fact;
import com.txscope.core.model.pool.PoolSnapshot;
import com.txscope.core.model.transaction.OpenTransaction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds a diagnostic report from available collectors.
 */
public class ReportService {

    private final PoolSnapshotCollector poolCollector;
    private final TransactionTracker transactionTracker;

    public ReportService(PoolSnapshotCollector poolCollector, TransactionTracker transactionTracker) {
        this.poolCollector = poolCollector;
        this.transactionTracker = transactionTracker;
    }

    public DiagnosticReport generateReport() {
        Instant now = Instant.now();
        DiagnosticReport.Builder builder = DiagnosticReport.builder().generatedAt(now);

        Optional<PoolSnapshot> poolOpt = poolCollector.collect();
        if (poolOpt.isEmpty()) {
            builder.addInsufficientEvidence(
                    "Pool snapshot unavailable (DataSource is not HikariCP or pool not started)");
        } else {
            PoolSnapshot pool = poolOpt.get();
            builder.pool(pool);
            for (Fact fact : factsFromPool(pool)) {
                builder.addFact(fact);
            }
            if (pool.getActive() == 0 && pool.getPending() == 0) {
                builder.addInsufficientEvidence(
                        "Pool is idle — run a scenario load script then request the report again");
            }
        }

        List<OpenTransaction> openTxs = transactionTracker.snapshot();
        for (Fact fact : factsFromTransactions(openTxs)) {
            builder.addFact(fact);
        }

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
}