package com.txscope.core.collector.postgres;


import com.txscope.core.model.PostgresSample;
import com.txscope.core.model.postgres.PgSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads pg_stat_activity via a dedicated diagnostic DataSource when possible.
 */
public class PostgresActivityCollector {

    private static final Logger log = LoggerFactory.getLogger(PostgresActivityCollector.class);

    private static final String SESSIONS_SQL = """
            SELECT
                a.pid,
                a.state,
                a.wait_event_type,
                a.wait_event,
                a.application_name,
                EXTRACT(EPOCH FROM (now() - a.state_change))::bigint AS seconds_in_state,
                (
                  a.wait_event_type = 'Lock'
                  OR cardinality(pg_blocking_pids(a.pid)) > 0
                ) AS blocked
            FROM pg_stat_activity a
            WHERE a.datname = current_database()
              AND a.pid <> pg_backend_pid()
              AND a.backend_type = 'client backend'
            ORDER BY a.state_change NULLS LAST
            """;


    private final DataSource dataSource;
    private final int networkTimeoutMs;
    private final int queryTimeoutSec;

    /**
     * @param dataSource prefer the monitor pool; may be null → UNAVAILABLE
     */
    public PostgresActivityCollector(DataSource dataSource, int networkTimeoutMs, int queryTimeoutSec) {
        this.dataSource = dataSource;
        this.networkTimeoutMs = networkTimeoutMs;
        this.queryTimeoutSec = queryTimeoutSec;
    }

    /** Convenience: 2s network, 2s query. */
    public PostgresActivityCollector(DataSource dataSource) {
        this(dataSource, 2000, 2);
    }

    public PostgresSample collect() {
        if (dataSource == null) {
            return PostgresSample.unavailable("monitor DataSource not configured");
        }

        try (Connection conn = dataSource.getConnection()) {
            try {
                conn.setNetworkTimeout(Runnable::run, networkTimeoutMs);
            } catch (AbstractMethodError | SQLException ignored) {
                // driver may not support network timeout
            }
            try (PreparedStatement ps = conn.prepareStatement(SESSIONS_SQL)) {
                ps.setQueryTimeout(queryTimeoutSec);
                try (ResultSet rs = ps.executeQuery()) {
                    List<PgSession> sessions = new ArrayList<>();
                    while (rs.next()) {
                        sessions.add(new PgSession(
                                rs.getInt("pid"),
                                rs.getString("state"),
                                rs.getString("wait_event_type"),
                                rs.getString("wait_event"),
                                rs.getString("application_name"),
                                (Long) rs.getObject("seconds_in_state"),
                                rs.getBoolean("blocked")
                        ));
                    }
                    return PostgresSample.ok(sessions);
                }
            }
        } catch (SQLTimeoutException e) {
            log.warn("Postgres diagnostic query timed out: {}", e.getMessage());
            return PostgresSample.timeout(e.getMessage());
        } catch (SQLException e) {
            log.warn("Postgres diagnostic collection failed: {}", e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.toLowerCase().contains("timeout") || msg.toLowerCase().contains("timed out")) {
                return PostgresSample.timeout(msg);
            }
            return PostgresSample.failed(msg);
        } catch (Exception e) {
            log.warn("Postgres diagnostic collection failed: {}", e.getMessage());
            return PostgresSample.failed(e.getMessage());
        }
    }
}