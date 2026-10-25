package com.txscope.core.model;

/**
 * Point-in-time snapshot of a HikariCP pool.
 * Only observed numbers — no interpretation.
 */
public class PoolSnapshot {

    private final String poolName;
    private final int active;
    private final int idle;
    private final int pending;
    private final int total;
    private final int max;
    private final int min;
    private final long threadsAwaitingConnection;
    /** Connections that timed out while waiting for a connection (lifetime counter if available). */
    private final long timeoutCount;

    public PoolSnapshot(
            String poolName,
            int active,
            int idle,
            int pending,
            int total,
            int max,
            int min,
            long threadsAwaitingConnection,
            long timeoutCount) {
        this.poolName = poolName;
        this.active = active;
        this.idle = idle;
        this.pending = pending;
        this.total = total;
        this.max = max;
        this.min = min;
        this.threadsAwaitingConnection = threadsAwaitingConnection;
        this.timeoutCount = timeoutCount;
    }

    public String getPoolName() {
        return poolName;
    }

    public int getActive() {
        return active;
    }

    public int getIdle() {
        return idle;
    }

    public int getPending() {
        return pending;
    }

    public int getTotal() {
        return total;
    }

    public int getMax() {
        return max;
    }

    public int getMin() {
        return min;
    }

    public long getThreadsAwaitingConnection() {
        return threadsAwaitingConnection;
    }

    public long getTimeoutCount() {
        return timeoutCount;
    }

    /** True when every connection is in use. */
    public boolean isFullyActive() {
        return max > 0 && active >= max;
    }

    /** True when threads are waiting for a connection. */
    public boolean hasWaiters() {
        return pending > 0 || threadsAwaitingConnection > 0;
    }
}
