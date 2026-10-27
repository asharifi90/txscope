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


        boolean poolSaturated = pool != null && pool.isFullyActive();
        boolean hasWaiters = pool != null && pool.hasWaiters();
        boolean longTx = facts.stream().anyMatch(f ->
                "transaction".equals(f.getSource())
                        && f.getText() != null
                        && f.getText().contains("Long-running transaction"));
        boolean openTx = facts.stream().anyMatch(f ->
                "transaction".equals(f.getSource())
                        && f.getText() != null
                        && f.getText().contains("currently open"));

        boolean idleInTx = facts.stream().anyMatch(f ->
                "postgres".equals(f.getSource())
                        && f.getText() != null
                        && f.getText().contains("idle in transaction")
                        && !f.getText().contains("idle_in_transaction=0"));

        boolean blocked = facts.stream().anyMatch(f ->
                "postgres".equals(f.getSource())
                        && f.getText() != null
                        && (f.getText().contains(" is blocked")
                        || f.getText().matches(".*blocked=[1-9].*")));

        boolean longActiveSql = facts.stream().anyMatch(f ->
                "postgres".equals(f.getSource())
                        && f.getText() != null
                        && f.getText().contains(" active for "));
        boolean s2LockServices = facts.stream().anyMatch(f ->
                "transaction".equals(f.getSource())
                        && f.getText() != null
                        && f.getText().contains("S2LockService.holdLock"))
                && facts.stream().anyMatch(f ->
                "transaction".equals(f.getSource())
                        && f.getText() != null
                        && f.getText().contains("S2LockService.waitForLock"));

        if ((poolSaturated || hasWaiters) && (blocked || s2LockServices)) {
            result.add(new Hypothesis(
                    "H2",
                    "PostgreSQL lock contention — sessions blocked, pool filling with waiters",
                    blocked ? "medium" : "low",
                    supportingIds(facts, "pool", "transaction", "postgres"),
                    List.of(
                            "Inspect pg_locks and pg_stat_activity for wait_event=Lock",
                            "Check long-running transactions that hold locks",
                            "Review lock ordering and transaction duration"
                    )
            ));
        }

        // S1-like: pool full + long tx + idle in transaction (connection held outside SQL)
        if ((poolSaturated || hasWaiters) && (longTx || openTx) && !blocked && !longActiveSql) {
            // Prefer H1 even if Postgres idle-in-tx fact is weak
            result.add(new Hypothesis(
                    "H1",
                    "Connection held across non-database work inside a transaction boundary",
                    idleInTx ? "medium" : "low",
                    supportingIds(facts, "pool", "transaction", "postgres"),
                    List.of(
                            "Search for external HTTP/RPC or Thread.sleep inside @Transactional methods",
                            "Narrow transaction boundaries to DB-only sections",
                            "Consider moving external calls outside the transaction"
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
