package com.txscope.core.model.postgres;

/**
 * One row from pg_stat_activity (read-only, no query text parameters).
 */
public class PgSession {

    private final int pid;
    private final String state;
    private final String waitEventType;
    private final String waitEvent;
    private final String applicationName;
    private final Long secondsInState;
    private final boolean blocked;

    public PgSession(
            int pid,
            String state,
            String waitEventType,
            String waitEvent,
            String applicationName,
            Long secondsInState,
            boolean blocked) {
        this.pid = pid;
        this.state = state;
        this.waitEventType = waitEventType;
        this.waitEvent = waitEvent;
        this.applicationName = applicationName;
        this.secondsInState = secondsInState;
        this.blocked = blocked;
    }

    public int getPid() {
        return pid;
    }

    public String getState() {
        return state;
    }

    public String getWaitEventType() {
        return waitEventType;
    }

    public String getWaitEvent() {
        return waitEvent;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public Long getSecondsInState() {
        return secondsInState;
    }

    public boolean isBlocked() {
        return blocked;
    }
}
