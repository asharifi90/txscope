package com.txscope.demo;

import com.txscope.core.collector.pool.PoolSnapshotCollector;
import com.txscope.core.collector.postgres.PostgresActivityCollector;
import com.txscope.core.collector.transaction.TransactionTracker;
import com.txscope.core.collector.transaction.TransactionTrackingAspect;
import com.txscope.core.report.HypothesisEngine;
import com.txscope.core.report.ReportService;
import com.txscope.starter.TxScopeReportController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import javax.sql.DataSource;

/**
 * Explicit wiring for the demo app (avoids relying on starter auto-config).
 */
@Configuration
@EnableAspectJAutoProxy
public class TxScopeDemoConfig {

    @Bean
    public PoolSnapshotCollector poolSnapshotCollector(DataSource dataSource) {
        return new PoolSnapshotCollector(dataSource);
    }

    @Bean
    public TransactionTracker transactionTracker() {
        return new TransactionTracker();
    }

    @Bean
    public TransactionTrackingAspect transactionTrackingAspect(TransactionTracker transactionTracker) {
        return new TransactionTrackingAspect(transactionTracker);
    }

    @Bean
    public PostgresActivityCollector postgresActivityCollector(DataSource dataSource) {
        return new PostgresActivityCollector(dataSource);
    }

    @Bean
    public HypothesisEngine hypothesisEngine() {
        return new HypothesisEngine();
    }

    @Bean
    public ReportService reportService(
            PoolSnapshotCollector poolSnapshotCollector,
            TransactionTracker transactionTracker,
            PostgresActivityCollector postgresActivityCollector,
            HypothesisEngine hypothesisEngine) {
        return new ReportService(
                poolSnapshotCollector,
                transactionTracker,
                postgresActivityCollector,
                hypothesisEngine);
    }

    @Bean
    public TxScopeReportController txScopeReportController(ReportService reportService) {
        return new TxScopeReportController(reportService);
    }
}
