package com.txscope.starter;


import com.txscope.core.collector.pool.PoolSnapshotCollector;
import com.txscope.core.collector.postgres.PostgresActivityCollector;
import com.txscope.core.collector.transaction.TransactionTracker;
import com.txscope.core.collector.transaction.TransactionTrackingAspect;
import com.txscope.core.report.HypothesisEngine;
import com.txscope.core.report.ReportService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnClass(DataSource.class)
@ConditionalOnBean(DataSource.class)
@EnableAspectJAutoProxy
public class TxScopeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PoolSnapshotCollector poolSnapshotCollector(DataSource dataSource) {
        return new PoolSnapshotCollector(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionTracker transactionTracker() {
        return new TransactionTracker();
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionTrackingAspect transactionTrackingAspect(TransactionTracker transactionTracker) {
        return new TransactionTrackingAspect(transactionTracker);
    }

    @Bean
    @ConditionalOnMissingBean
    public PostgresActivityCollector postgresActivityCollector(DataSource dataSource) {
        return new PostgresActivityCollector(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public HypothesisEngine hypothesisEngine() {
        return new HypothesisEngine();
    }

    @Bean
    @ConditionalOnMissingBean
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
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean
    public TxScopeReportController txScopeReportController(ReportService reportService) {
        return new TxScopeReportController(reportService);
    }
}