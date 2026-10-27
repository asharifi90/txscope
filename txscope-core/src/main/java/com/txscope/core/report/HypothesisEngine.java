package com.txscope.core.report;

import com.txscope.core.model.Hypothesis;
import com.txscope.core.model.pool.Fact;
import com.txscope.core.model.pool.PoolSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rule-based engine: facts → labelled hypotheses (S1–S4 oriented).
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

        // Strict: real blocked fact, not "blocked=0" in a summary line
        boolean blocked = facts.stream().anyMatch(f ->
                "postgres".equals(f.getSource())
                        && f.getText() != null
                        && (f.getText().contains(" is blocked")
                        || f.getText().matches(".*\\bblocked=[1-9].*")));

        boolean longActiveSql = facts.stream().anyMatch(f ->
                "postgres".equals(f.getSource())
                        && f.getText() != null
                        && f.getText().contains(" active for "));

        // S2 demo / lock scenario: holder + waiter visible in Spring txs
        boolean s2LockPattern = facts.stream().anyMatch(f ->
                "transaction".equals(f.getSource())
                        && f.getText() != null
                        && f.getText().contains("S2LockService.holdLock"))
                && facts.stream().anyMatch(f ->
                "transaction".equals(f.getSource())
                        && f.getText() != null
                        && f.getText().contains("S2LockService.waitForLock"));

        // --- H2: lock contention (before H1) ---
        if ((poolSaturated || hasWaiters) && (blocked || s2LockPattern)) {
            result.add(new Hypothesis(
                    "H2",
                    "PostgreSQL lock contention — sessions blocked or waiting on the same lock",
                    blocked ? "medium" : "low",
                    supportingIds(facts, "pool", "transaction", "postgres"),
                    List.of(
                            "Inspect pg_locks and pg_stat_activity (wait_event_type=Lock)",
                            "Find the session that holds the lock and how long its transaction has been open",
                            "Review lock ordering and transaction duration"
                    )
            ));
        }

        // --- H1: held across non-DB work (skip if H2 already matched) ---
        if (result.stream().noneMatch(h -> "H2".equals(h.getId()))
                && (poolSaturated || hasWaiters)
                && (longTx || openTx)
                && !blocked
                && !longActiveSql) {
            result.add(new Hypothesis(
                    "H1",
                    "Connection held across non-database work inside a transaction boundary",
                    (idleInTx || (poolSaturated && longTx)) ? "medium" : "low",
                    supportingIds(facts, "pool", "transaction", "postgres"),
                    List.of(
                            "Search for external HTTP/RPC or Thread.sleep inside @Transactional methods",
                            "Narrow transaction boundaries to DB-only sections",
                            "Consider moving external calls outside the transaction"
                    )
            ));
        }

        // --- H3: slow SQL ---
        if ((poolSaturated || hasWaiters) && longActiveSql && !idleInTx && !s2LockPattern) {
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

        // --- H4: possible leak ---
        if (poolSaturated && !openTx && !idleInTx && !blocked && !longActiveSql && !s2LockPattern) {
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

        // --- H0: saturated but unclear ---
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

    private static List<String> supportingIds(List<Fact> facts, String... sources) {
        List<String> sourceList = List.of(sources);
        return facts.stream()
                .filter(f -> sourceList.contains(f.getSource()))
                .map(Fact::getId)
                .collect(Collectors.toList());
    }
}