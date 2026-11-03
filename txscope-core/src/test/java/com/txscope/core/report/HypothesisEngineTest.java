package com.txscope.core.report;

import com.txscope.core.model.Hypothesis;
import com.txscope.core.model.pool.Fact;
import com.txscope.core.model.pool.PoolSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


class HypothesisEngineTest {

    HypothesisEngine engine;

    @BeforeEach
    void setUp() {
        engine = new HypothesisEngine();
    }

    private static PoolSnapshot saturatedPool() {
        return new PoolSnapshot(
                "TxScopeHikariPool",
                5, 0, 2, 5, 5, 2, 2, 0
        );
    }

    @Test
    void poolSaturated_longTx_noLock_shouldPreferH1() {
        List<Fact> facts = List.of(
                new Fact("F1", "All pool connections are active (5/5)", "pool"),
                new Fact("T1", "3 Spring transaction(s) currently open", "transaction"),
                new Fact("T2", "Long-running transaction name=OrderService.place durationMs=2000", "transaction")
        );

        List<Hypothesis> out = engine.evaluate(saturatedPool(), facts);

        assertThat(out).extracting(Hypothesis::getId).contains("H1");
        assertThat(out).extracting(Hypothesis::getId).doesNotContain("H2");
    }

    @Test
    void poolSaturated_blockedSessions_shouldPreferH2() {
        List<Fact> facts = List.of(
                new Fact("F1", "All pool connections are active (5/5)", "pool"),
                new Fact("P1", "Session pid=10 is blocked (wait_event=Lock/advisory)", "postgres")
        );

        List<Hypothesis> out = engine.evaluate(saturatedPool(), facts);

        assertThat(out).extracting(Hypothesis::getId).contains("H2");
    }

    @Test
    void poolSaturated_activeForSql_shouldPreferH3() {
        List<Fact> facts = List.of(
                new Fact("F1", "All pool connections are active (5/5)", "pool"),
                new Fact("P1", "Session pid=10 active for 10s wait_event=PgSleep", "postgres")
        );

        List<Hypothesis> out = engine.evaluate(saturatedPool(), facts);

        assertThat(out).extracting(Hypothesis::getId).contains("H3");
    }

    @Test
    void poolSaturated_noTx_noPgWork_shouldPreferH4() {
        List<Fact> facts = List.of(
                new Fact("F1", "All pool connections are active (5/5)", "pool"),
                new Fact("F2", "No idle connections available (idle=0, active=5)", "pool")
        );

        List<Hypothesis> out = engine.evaluate(saturatedPool(), facts);

        assertThat(out).extracting(Hypothesis::getId).contains("H4");
    }

    @Test
    void blockedWordInSummaryOnly_shouldNotForceH2() {
        // "blocked=0" must not count as blocked
        List<Fact> facts = List.of(
                new Fact("F1", "All pool connections are active (5/5)", "pool"),
                new Fact("T1", "2 Spring transaction(s) currently open", "transaction"),
                new Fact("T2", "Long-running transaction name=SomeService.work durationMs=3000", "transaction"),
                new Fact("P1", "PostgreSQL sessions: total=1 active=0 idle_in_transaction=0 blocked=0", "postgres")
        );

        List<Hypothesis> out = engine.evaluate(saturatedPool(), facts);

        assertThat(out).extracting(Hypothesis::getId).contains("H1");
        assertThat(out).extracting(Hypothesis::getId).doesNotContain("H2");
    }
}
