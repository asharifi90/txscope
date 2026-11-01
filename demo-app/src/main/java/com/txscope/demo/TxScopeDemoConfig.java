package com.txscope.demo;

import com.txscope.core.collector.pool.PoolSnapshotCollector;
import com.txscope.core.collector.postgres.PostgresActivityCollector;
import com.txscope.core.collector.transaction.TransactionTracker;
import com.txscope.core.collector.transaction.TransactionTrackingAspect;
import com.txscope.core.report.HypothesisEngine;
import com.txscope.core.report.ReportService;
import com.txscope.starter.TxScopeReportController;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Explicit wiring for the demo app (avoids relying on starter auto-config).
 */
@Configuration
@EnableAspectJAutoProxy
public class TxScopeDemoConfig {

    /**
     * Main application pool (business traffic).
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties properties) {
        HikariDataSource ds = properties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        ds.setPoolName("TxScopeHikariPool");
        return ds;
    }

    @Bean
    public PoolSnapshotCollector poolSnapshotCollector(
            @Qualifier("dataSource") DataSource dataSource) {
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

    /** Diagnostic pool (size 1) — Postgres sampling only. */
    @Bean(name = "txScopeMonitorDataSource")
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
        cfg.setConnectionTimeout(2000);
        cfg.setValidationTimeout(1000);
        cfg.setInitializationFailTimeout(-1);
        cfg.setReadOnly(true);
        cfg.setAutoCommit(true);
        return new com.zaxxer.hikari.HikariDataSource(cfg);
    }

    @Bean
    public PostgresActivityCollector postgresActivityCollector(
            @Qualifier("txScopeMonitorDataSource") DataSource monitorDs) {
        return new PostgresActivityCollector(monitorDs, 2000, 2);
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
}
