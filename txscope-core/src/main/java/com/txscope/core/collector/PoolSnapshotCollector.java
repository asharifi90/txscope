package com.txscope.core.collector;

import com.txscope.core.model.PoolSnapshot;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Optional;

/**
 * Collects a point-in-time snapshot of the HikariCP pool.
 * <p>
 * Reads only from Hikari's public API / MXBean — no guesswork.
 */
public class PoolSnapshotCollector {

    private static final Logger log = LoggerFactory.getLogger(PoolSnapshotCollector.class);

    private final DataSource dataSource;

    public PoolSnapshotCollector(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * @return snapshot if the DataSource is HikariCP, empty otherwise
     */
    public Optional<PoolSnapshot> collect() {
        HikariDataSource hikari = unwrapHikari(dataSource);
        if (hikari == null) {
            log.warn("DataSource is not HikariCP — cannot collect pool snapshot");
            return Optional.empty();
        }

        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        if (pool == null) {
            // Pool not started yet
            log.warn("Hikari pool MXBean not available yet");
            return Optional.empty();
        }

        String name = hikari.getPoolName() != null ? hikari.getPoolName() : "unknown";
        int active = pool.getActiveConnections();
        int idle = pool.getIdleConnections();
        int total = pool.getTotalConnections();
        int threadsAwaiting = pool.getThreadsAwaitingConnection();
        int max = hikari.getMaximumPoolSize();
        int min = hikari.getMinimumIdle();

        long timeoutCount = 0L;

        int pending = threadsAwaiting;

        PoolSnapshot snapshot = new PoolSnapshot(
                name,
                active,
                idle,
                pending,
                total,
                max,
                min,
                threadsAwaiting,
                timeoutCount
        );

        log.debug("Pool snapshot: active={}/{} idle={} pending={}", active, max, idle, pending);
        return Optional.of(snapshot);
    }

    private static HikariDataSource unwrapHikari(DataSource ds) {
        if (ds == null) {
            return null;
        }
        if (ds instanceof HikariDataSource hikari) {
            return hikari;
        }
        try {
            if (ds.isWrapperFor(HikariDataSource.class)) {
                return ds.unwrap(HikariDataSource.class);
            }
        } catch (Exception e) {
            log.debug("Could not unwrap DataSource to HikariDataSource: {}", e.getMessage());
        }
        return null;
    }
}
