package com.txscope.starter;

import com.txscope.core.collector.PoolSnapshotCollector;
import com.txscope.core.report.ReportService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Wires TxScope collectors and the report endpoint when a DataSource is present.
 */
@AutoConfiguration
@ConditionalOnClass(DataSource.class)
@ConditionalOnBean(DataSource.class)
public class TxScopeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PoolSnapshotCollector poolSnapshotCollector(DataSource dataSource) {
        return new PoolSnapshotCollector(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReportService reportService(PoolSnapshotCollector poolSnapshotCollector) {
        return new ReportService(poolSnapshotCollector);
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean
    public TxScopeReportController txScopeReportController(ReportService reportService) {
        return new TxScopeReportController(reportService);
    }
}
