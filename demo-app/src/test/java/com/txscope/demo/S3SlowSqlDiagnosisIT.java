package com.txscope.demo;

import com.txscope.core.model.Hypothesis;
import com.txscope.core.model.pool.DiagnosticReport;
import com.txscope.core.report.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class S3SlowSqlDiagnosisIT extends AbstractTxScopeIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ReportService reportService;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void s3_shouldProduceH3() throws Exception {
        String base = "http://localhost:" + port;
        ExecutorService exec = Executors.newFixedThreadPool(6);
        try {
            for (int i = 0; i < 6; i++) {
                exec.submit(() ->
                        rest.getForEntity(base + "/scenarios/s3?seconds=8", String.class));
            }
            Thread.sleep(2500);

            DiagnosticReport report = reportService.generateReport();

            assertThat(report.getHypotheses())
                    .extracting(Hypothesis::getId)
                    .contains("H3");
        } finally {
            exec.shutdownNow();
        }
    }
}
