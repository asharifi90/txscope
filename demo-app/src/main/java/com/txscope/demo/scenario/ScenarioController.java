package com.txscope.demo.scenario;

import com.txscope.demo.domain.Item;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Intentional failure endpoints for TxScope demos (S1–S4).
 */
@RestController
@RequestMapping("/scenarios")
public class ScenarioController {

    private final S1ExternalCallInTransactionService s1Service;
    private final S2LockService s2Service;
    private final S3SlowSqlService s3Service;
    private final S4ConnectionLeakService s4Service;

    public ScenarioController(
            S1ExternalCallInTransactionService s1Service,
            S2LockService s2Service,
            S3SlowSqlService s3Service,
            S4ConnectionLeakService s4Service) {
        this.s1Service = s1Service;
        this.s2Service = s2Service;
        this.s3Service = s3Service;
        this.s4Service = s4Service;
    }

    /**
     * S1 – External work inside a transaction.
     * Connection held while doing non-DB work → pool saturates, Postgres looks idle.
     */
    @GetMapping("/s1")
    public ResponseEntity<Item> s1(@RequestParam(defaultValue = "3000") long delayMs) {
        return ResponseEntity.ok(s1Service.execute(delayMs));
    }

    /**
     * S2a – Hold a PostgreSQL advisory lock for a while.
     * Start this first, then call /s2/wait concurrently.
     */
    @GetMapping("/s2/hold")
    public ResponseEntity<Map<String, Object>> s2Hold(@RequestParam(defaultValue = "10000") long holdMs) {
        s2Service.holdLock(holdMs);
        return ResponseEntity.ok(Map.of(
                "scenario", "S2",
                "action", "lock-held-and-released",
                "holdMs", holdMs
        ));
    }

    /**
     * S2b – Wait for the same lock. Blocks while another session holds it.
     * Connections stay borrowed during the wait → pool pressure.
     */
    @GetMapping("/s2/wait")
    public ResponseEntity<Map<String, Object>> s2Wait(@RequestParam(defaultValue = "5000") long timeoutHintMs) {
        s2Service.waitForLock(timeoutHintMs);
        return ResponseEntity.ok(Map.of(
                "scenario", "S2",
                "action", "lock-acquired-after-wait"
        ));
    }

    /**
     * S3 – Slow SQL (pg_sleep). Connection legitimately busy in the database.
     */
    @GetMapping("/s3")
    public ResponseEntity<Map<String, Object>> s3(@RequestParam(defaultValue = "5") double seconds) {
        s3Service.executeSlowQuery(seconds);
        return ResponseEntity.ok(Map.of(
                "scenario", "S3",
                "action", "slow-sql-finished",
                "seconds", seconds
        ));
    }

    /**
     * S4 – Leak one connection (never returned to the pool).
     * Call several times until the pool is exhausted.
     */
    @PostMapping("/s4/leak")
    public ResponseEntity<Map<String, Object>> s4Leak() {
        int total = s4Service.leakOne();
        return ResponseEntity.ok(Map.of(
                "scenario", "S4",
                "action", "connection-leaked",
                "leakedTotal", total
        ));
    }

    /**
     * S4 – How many connections this process is currently holding.
     */
    @GetMapping("/s4/status")
    public ResponseEntity<Map<String, Object>> s4Status() {
        return ResponseEntity.ok(Map.of(
                "scenario", "S4",
                "leakedTotal", s4Service.leakedCount()
        ));
    }

    /**
     * S4 – Release leaked connections (cleanup between demos).
     */
    @PostMapping("/s4/release")
    public ResponseEntity<Map<String, Object>> s4Release() {
        s4Service.releaseAll();
        return ResponseEntity.ok(Map.of(
                "scenario", "S4",
                "action", "all-leaked-connections-released"
        ));
    }

    @GetMapping("/health-check")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("demo-app is up");
    }
}
