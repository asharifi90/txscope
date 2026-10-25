package com.txscope.core.collector.transaction;

import com.txscope.core.model.transaction.OpenTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks currently open Spring transactions.
 * <p>
 * Register a synchronization when a transaction starts; remove it when it completes.
 * Thread-safe snapshot for the report.
 */
public class TransactionTracker {

    private static final Logger log = LoggerFactory.getLogger(TransactionTracker.class);

    /** txId → start info */
    private final Map<String, TxInfo> open = new ConcurrentHashMap<>();

    /**
     * Call this when you know a Spring transaction is active on the current thread
     * (e.g. from an aspect or a TransactionSynchronizationManager check).
     * Registers a synchronization so we learn when the tx ends.
     */
    public void onTransactionActive(String name) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        if (TransactionSynchronizationManager.getSynchronizations().stream()
                .anyMatch(s -> s instanceof TrackingSynchronization)) {
            return;
        }

        String id = UUID.randomUUID().toString().substring(0, 8);
        Instant startedAt = Instant.now();
        String txName = (name != null && !name.isBlank())
                ? name
                : TransactionSynchronizationManager.getCurrentTransactionName();
        if (txName == null) {
            txName = "anonymous";
        }

        open.put(id, new TxInfo(id, txName, startedAt));
        TransactionSynchronizationManager.registerSynchronization(new TrackingSynchronization(id));
        log.debug("Tracking transaction id={} name={}", id, txName);
    }

    /**
     * Snapshot of transactions still open at this moment.
     */
    public List<OpenTransaction> snapshot() {
        Instant now = Instant.now();
        List<OpenTransaction> result = new ArrayList<>();
        for (TxInfo info : open.values()) {
            long durationMs = now.toEpochMilli() - info.startedAt.toEpochMilli();
            result.add(new OpenTransaction(info.id, info.name, info.startedAt, durationMs));
        }
        return result;
    }

    private void remove(String id) {
        open.remove(id);
        log.debug("Transaction completed id={}", id);
    }

    private static final class TxInfo {
        final String id;
        final String name;
        final Instant startedAt;

        TxInfo(String id, String name, Instant startedAt) {
            this.id = id;
            this.name = name;
            this.startedAt = startedAt;
        }
    }

    private final class TrackingSynchronization implements TransactionSynchronization {
        private final String id;

        TrackingSynchronization(String id) {
            this.id = id;
        }

        @Override
        public void afterCompletion(int status) {
            remove(id);
        }
    }
}