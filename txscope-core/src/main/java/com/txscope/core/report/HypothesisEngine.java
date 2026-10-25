package com.txscope.core.report;



import com.txscope.core.model.Hypothesis;
import com.txscope.core.model.pool.Fact;
import com.txscope.core.model.pool.PoolSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rule-based engine: turns facts into labelled hypotheses (S1–S4 oriented).
 * Never invents links that are not supported by facts.
 */
public class HypothesisEngine {

    public List<Hypothesis> evaluate(PoolSnapshot pool, List<Fact> facts) {
        List<Hypothesis> result = new ArrayList<>();
        if (facts == null || facts.isEmpty()) {
            return result;
        }

        boolean poolSaturated = hasSourceText(facts, "pool", "active")
                && (pool != null && pool.isFullyActive());
        boolean hasWaiters = pool != null && pool.hasWaiters()
                || hasSourceText(facts, "pool", "waiting");
        boolean longTx = hasSourceText(facts, "transaction", "Long-running");
        boolean openTx = hasSourceText(facts, "transaction", "currently open");
        boolean idleInTx = hasSourceText(facts, "postgres", "idle in transaction");
        boolean blocked = hasSourceText(facts, "postgres", "blocked");
        boolean longActiveSql = hasSourceText(facts, "postgres", "active for");

        // S1-like: pool full + long tx + idle in transaction (connection held outside SQL)
        if ((poolSaturated || hasWaiters) && (longTx || openTx) && idleInTx && !blocked && !longActiveSql) {
            result.add(new Hypothesis(
                    "H1",
                    "Connection held across non-database work inside a transaction boundary",
                    "medium",
                    supportingIds(facts, "pool", "transaction", "postgres"),
                    List.of(
                            "Search for external HTTP/RPC or Thread.sleep inside @Transactional methods",
                            "Narrow transaction boundaries to DB-only sections",
                            "Consider moving external calls outside the transaction"
                    )
            ));
        }

        // S2-like: pool pressure + blocked sessions
        if ((poolSaturated || hasWaiters) && blocked) {
            result.add(new Hypothesis(
                    "H2",
                    "PostgreSQL lock contention — sessions blocked, pool filling with waiters",
                    "medium",
                    supportingIds(facts, "pool", "postgres"),
                    List.of(
                            "Inspect pg_locks and pg_blocking_pids for the blocker",
                            "Check long-running transactions that hold locks",
                            "Review isolation level and lock ordering"
                    )
            ));
        }

        // S3-like: pool full + long active SQL in Postgres
        if ((poolSaturated || hasWaiters) && longActiveSql && !idleInTx) {
            result.add(new Hypothesis(
                    "H3",
                    "Connections busy executing slow SQL in PostgreSQL",
                    "medium",
                    supportingIds(facts, "pool", "postgres"),
                    List.of(
                            "Identify slow queries (pg_stat_statements or logs)",
                            "Check missing indexes / sequential scans",
                            "Consider statement timeout and query tuning"
                    )
            ));
        }

        // S4-like: pool saturated, no open Spring tx, no idle-in-tx, no blocked — possible leak
        if (poolSaturated && !openTx && !idleInTx && !blocked && !longActiveSql) {
            result.add(new Hypothesis(
                    "H4",
                    "Possible connection leak — pool exhausted without clear in-flight DB work",
                    "low",
                    supportingIds(facts, "pool"),
                    List.of(
                            "Enable Hikari leakDetectionThreshold and check logs for stack traces",
                            "Search for getConnection() without close / missing try-with-resources",
                            "Verify connection is not held outside a managed transaction incorrectly"
                    )
            ));
        }

        // Generic saturation with little detail
        if (result.isEmpty() && (poolSaturated || hasWaiters)) {
            result.add(new Hypothesis(
                    "H0",
                    "Pool saturation detected but root cause is unclear from current facts",
                    "low",
                    supportingIds(facts, "pool"),
                    List.of(
                            "Re-run report while load is active",
                            "Confirm transaction tracking and PostgreSQL collectors are working",
                            "Compare with manual pg_stat_activity and Hikari metrics"
                    )
            ));
        }

        return result;
    }

    private static boolean hasSourceText(List<Fact> facts, String source, String textPart) {
        String lower = textPart.toLowerCase();
        return facts.stream()
                .anyMatch(f -> source.equals(f.getSource())
                        && f.getText() != null
                        && f.getText().toLowerCase().contains(lower));
    }

    private static List<String> supportingIds(List<Fact> facts, String... sources) {
        List<String> sourceList = List.of(sources);
        return facts.stream()
                .filter(f -> sourceList.contains(f.getSource()))
                .map(Fact::getId)
                .collect(Collectors.toList());
    }
}
