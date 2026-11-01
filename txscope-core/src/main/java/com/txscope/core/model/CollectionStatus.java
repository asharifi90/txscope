package com.txscope.core.model;

/**
 * Outcome of a collector run. Never treat FAILED/TIMEOUT as "zero rows".
 */
public enum CollectionStatus {
    /** Measurement succeeded and may contain zero or more rows. */
    OK,
    /** Succeeded; no matching rows (real empty). */
    EMPTY,
    /** Could not obtain a connection or run the query in time. */
    TIMEOUT,
    /** Error (permissions, SQL, unexpected). */
    FAILED,
    /** Collector not configured (e.g. no monitor DataSource). */
    UNAVAILABLE
}
