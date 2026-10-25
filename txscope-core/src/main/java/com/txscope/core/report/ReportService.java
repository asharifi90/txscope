package com.txscope.core.report;

import com.txscope.core.collector.PoolSnapshotCollector;
import com.txscope.core.model.DiagnosticReport;
import com.txscope.core.model.Fact;
import com.txscope.core.model.PoolSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds a diagnostic report from available collectors.
 */
public class ReportService {

    private final PoolSnapshotCollector poolCollector;

    public ReportService(PoolSnapshotCollector poolCollector) {
        this.poolCollector = poolCollector;
    }

    public DiagnosticReport generateReport() {
        Instant now = Instant.now();
        Optional<PoolSnapshot> poolOpt = poolCollector.collect();

        DiagnosticReport.Builder builder = DiagnosticReport.builder().generatedAt(now);

        if (poolOpt.isEmpty()) {
            builder.addInsufficientEvidence(
                    "Pool snapshot unavailable (DataSource is not HikariCP or pool not started)");
            return builder.build();
        }

        PoolSnapshot pool = poolOpt.get();
        builder.pool(pool);

        for (Fact fact : factsFromPool(pool)) {
            builder.addFact(fact);
        }

        if (pool.getActive() == 0 && pool.getPending() == 0) {
            builder.addInsufficientEvidence(
                    "Pool is idle — run a scenario load script (e.g. ./scripts/load-s1.sh) then request the report again");
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
}
