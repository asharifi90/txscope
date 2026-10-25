package com.txscope.core.collector.postgres;


import com.txscope.core.model.postgres.PgSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only snapshot of PostgreSQL sessions for the current database.
 * Does not collect SQL bind parameters or full query text beyond state info.
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
                EXISTS (
                    SELECT 1 FROM pg_locks l1
                    JOIN pg_locks l2 ON l1.locktype = l2.locktype
                        AND l1.database IS NOT DISTINCT FROM l2.database
                        AND l1.relation IS NOT DISTINCT FROM l2.relation
                        AND l1.page IS NOT DISTINCT FROM l2.page
                        AND l1.tuple IS NOT DISTINCT FROM l2.tuple
                        AND l1.virtualxid IS NOT DISTINCT FROM l2.virtualxid
                        AND l1.transactionid IS NOT DISTINCT FROM l2.transactionid
                        AND l1.classid IS NOT DISTINCT FROM l2.classid
                        AND l1.objid IS NOT DISTINCT FROM l2.objid
                        AND l1.objsubid IS NOT DISTINCT FROM l2.objsubid
                        AND l1.pid != l2.pid
                    WHERE l1.pid = a.pid AND NOT l1.granted AND l2.granted
                ) AS blocked
            FROM pg_stat_activity a
            WHERE a.datname = current_database()
              AND a.pid != pg_backend_pid()
            ORDER BY a.state_change NULLS LAST
            """;

    private final DataSource dataSource;

    public PostgresActivityCollector(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<PgSession> collect() {
        List<PgSession> sessions = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SESSIONS_SQL);
             ResultSet rs = ps.executeQuery()) {

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
        } catch (SQLException e) {
            log.warn("Could not collect PostgreSQL activity: {}", e.getMessage());
        }
        log.debug("Collected {} PostgreSQL sessions", sessions.size());
        return sessions;
    }
}
