package com.txscope.demo.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scenario S3: Slow SQL.
 * <p>
 * The connection is legitimately busy executing a long-running query
 * (simulated with pg_sleep). Pool saturates because connections are
 * occupied by real database work.
 */
@Service
public class S3SlowSqlService {

    private static final Logger log = LoggerFactory.getLogger(S3SlowSqlService.class);

    private final JdbcTemplate jdbcTemplate;

    public S3SlowSqlService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Runs {@code SELECT pg_sleep(seconds)} inside a transaction.
     * The connection stays active in PostgreSQL for the whole duration.
     */
    @Transactional
    public void executeSlowQuery(double seconds) {
        log.info("S3: starting slow SQL (pg_sleep {})", seconds);
        jdbcTemplate.queryForObject("SELECT pg_sleep(?)", Object.class, seconds);
        log.info("S3: slow SQL finished");
    }
}
