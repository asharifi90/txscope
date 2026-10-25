package com.txscope.core.model.transaction;

import java.time.Duration;
import java.time.Instant;

/**
 * One Spring transaction that is still open (or was recently observed).
 */
public class OpenTransaction {

    private final String id;
    private final String name;
    private final Instant startedAt;
    private final long durationMs;

    public OpenTransaction(String id, String name, Instant startedAt, long durationMs) {
        this.id = id;
        this.name = name;
        this.startedAt = startedAt;
        this.durationMs = durationMs;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Duration getDuration() {
        return Duration.ofMillis(durationMs);
    }
}
