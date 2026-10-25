package com.txscope.demo.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Scenario S4: Connection leak (connection borrowed and never returned).
 * <p>
 * Intentionally obtains connections from the pool and does not close them.
 * After a few calls the pool is exhausted. Hikari leakDetectionThreshold
 * can surface stack traces for the leaked connections.
 */
@Service
public class S4ConnectionLeakService {

    private static final Logger log = LoggerFactory.getLogger(S4ConnectionLeakService.class);

    private final DataSource dataSource;

    /** Keeps strong references so connections are not GC'd / closed. */
    private final List<Connection> leakedConnections = new ArrayList<>();

    public S4ConnectionLeakService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Borrows one connection and never returns it.
     *
     * @return current number of leaked connections held by this service
     */
    public int leakOne() {
        try {
            Connection connection = dataSource.getConnection();
            // Touch the connection so it is clearly "in use"
            try (Statement st = connection.createStatement()) {
                st.execute("SELECT 1");
            }
            leakedConnections.add(connection);
            log.warn("S4: leaked a connection (total leaked = {})", leakedConnections.size());
            return leakedConnections.size();
        } catch (SQLException e) {
            log.error("S4: failed to leak connection (pool probably exhausted)", e);
            throw new IllegalStateException("Could not obtain connection to leak — pool may be exhausted", e);
        }
    }

    /**
     * Returns how many connections this service is currently holding.
     */
    public int leakedCount() {
        return leakedConnections.size();
    }

    /**
     * For cleanup between tests only — not used in the failure path.
     */
    public void releaseAll() {
        for (Connection c : leakedConnections) {
            try {
                c.close();
            } catch (SQLException e) {
                log.debug("Error closing leaked connection", e);
            }
        }
        leakedConnections.clear();
        log.info("S4: released all previously leaked connections");
    }
}
