package com.txscope.starter;


import com.txscope.core.collector.pool.PoolSnapshotCollector;
import com.txscope.core.collector.postgres.PostgresActivityCollector;
import com.txscope.core.collector.transaction.TransactionTracker;
import com.txscope.core.collector.transaction.TransactionTrackingAspect;
import com.txscope.core.report.HypothesisEngine;
import com.txscope.core.report.ReportService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
    public PoolSnapshotCollector poolSnapshotCollector(
            @Qualifier("dataSource") DataSource dataSource) {
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

    @Bean(name = "txScopeMonitorDataSource")
    @ConditionalOnMissingBean(name = "txScopeMonitorDataSource")
    public DataSource txScopeMonitorDataSource(
            @Value("${txscope.monitor.jdbc-url:${spring.datasource.url}}") String url,
            @Value("${txscope.monitor.username:${spring.datasource.username}}") String username,
            @Value("${txscope.monitor.password:${spring.datasource.password}}") String password) {

        com.zaxxer.hikari.HikariConfig cfg = new com.zaxxer.hikari.HikariConfig();
        cfg.setPoolName("TxScopeMonitorPool");
        cfg.setJdbcUrl(url);
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(1);
        cfg.setMinimumIdle(0);
        cfg.setConnectionTimeout(2000);      // fail fast
        cfg.setValidationTimeout(1000);
        cfg.setInitializationFailTimeout(-1); // don't kill app startup if monitor DB down
        cfg.setReadOnly(true);
        cfg.setAutoCommit(true);
        return new com.zaxxer.hikari.HikariDataSource(cfg);
    }

    @Bean
    @ConditionalOnMissingBean
    public PostgresActivityCollector postgresActivityCollector(
            @Qualifier("txScopeMonitorDataSource") DataSource monitorDs) {
        return new PostgresActivityCollector(monitorDs, 2000, 2);
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