package com.txscope.demo.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scenario S2: PostgreSQL lock contention.
 * <p>
 * One transaction takes an exclusive lock and holds it.
 * Concurrent callers block on the same lock → connections stay borrowed
 * while waiting → pool can saturate.
 */
@Service
public class S2LockService {

    private static final Logger log = LoggerFactory.getLogger(S2LockService.class);

    private final JdbcTemplate jdbcTemplate;

    public S2LockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Acquires an advisory lock (or row lock) and holds it for {@code holdMs} milliseconds.
     * Other sessions trying the same lock will wait.
     */
    @Transactional
    public void holdLock(long holdMs) {
        log.info("S2: acquiring advisory lock, will hold for {} ms", holdMs);

        // PostgreSQL advisory lock — simple and reliable for demos
        jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(42)", Integer.class);

        log.info("S2: lock acquired, sleeping {} ms while holding connection + lock", holdMs);
        sleep(holdMs);
        log.info("S2: releasing lock (transaction end)");
    }

    /**
     * Tries to acquire the same lock. Will block if another session holds it.
     */
    @Transactional
    public void waitForLock(long timeoutHintMs) {
        log.info("S2: waiting for advisory lock (may block up to pool connection-timeout)");
        jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(42)", Integer.class);
        log.info("S2: lock acquired after wait");
        // Hold briefly so the connection is visibly used
        sleep(Math.min(timeoutHintMs, 500));
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding lock", e);
        }
    }
}
