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
            (
              a.wait_event_type = 'Lock'
              OR EXISTS (
                SELECT 1
                FROM pg_locks blocked
                JOIN pg_locks blocking
                  ON blocking.locktype = blocked.locktype
                 AND blocking.database IS NOT DISTINCT FROM blocked.database
                 AND blocking.relation IS NOT DISTINCT FROM blocked.relation
                 AND blocking.page IS NOT DISTINCT FROM blocked.page
                 AND blocking.tuple IS NOT DISTINCT FROM blocked.tuple
                 AND blocking.virtualxid IS NOT DISTINCT FROM blocked.virtualxid
                 AND blocking.transactionid IS NOT DISTINCT FROM blocked.transactionid
                 AND blocking.classid IS NOT DISTINCT FROM blocked.classid
                 AND blocking.objid IS NOT DISTINCT FROM blocked.objid
                 AND blocking.objsubid IS NOT DISTINCT FROM blocked.objsubid
                 AND blocking.pid IS DISTINCT FROM blocked.pid
                WHERE blocked.pid = a.pid
                  AND NOT blocked.granted
                  AND blocking.granted
              )
            ) AS blocked
        FROM pg_stat_activity a
        WHERE a.datname = current_database()
          AND a.pid <> pg_backend_pid()
          AND a.backend_type = 'client backend'
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
