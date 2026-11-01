package com.txscope.core.model;

import com.txscope.core.model.postgres.PgSession;

import java.util.List;

public class PostgresSample {

    private final CollectionStatus status;
    private final List<PgSession> sessions;
    private final String detail;

    public PostgresSample(CollectionStatus status, List<PgSession> sessions, String detail) {
        this.status = status;
        this.sessions = sessions != null ? List.copyOf(sessions) : List.of();
        this.detail = detail;
    }

    public static PostgresSample ok(List<PgSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return new PostgresSample(CollectionStatus.EMPTY, List.of(), "no client backends matched");
        }
        return new PostgresSample(CollectionStatus.OK, sessions, null);
    }

    public static PostgresSample failed(String detail) {
        return new PostgresSample(CollectionStatus.FAILED, List.of(), detail);
    }

    public static PostgresSample timeout(String detail) {
        return new PostgresSample(CollectionStatus.TIMEOUT, List.of(), detail);
    }

    public static PostgresSample unavailable(String detail) {
        return new PostgresSample(CollectionStatus.UNAVAILABLE, List.of(), detail);
    }

    public CollectionStatus getStatus() {
        return status;
    }

    public List<PgSession> getSessions() {
        return sessions;
    }

    public String getDetail() {
        return detail;
    }
}