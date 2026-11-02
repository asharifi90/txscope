package com.txscope.core.model;

public record CollectorOutcome(
        String source,   //pool | transaction | postgres
        CollectionStatus status,
        String detail //nullable
) {}
