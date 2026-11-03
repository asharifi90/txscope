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

class S1ExternalWorkDiagnosisIT extends AbstractTxScopeIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ReportService reportService;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void s1_shouldProduceH1() throws Exception {
        String base = "http://localhost:" + port;
        ExecutorService exec = Executors.newFixedThreadPool(8);
        try {
            for (int i = 0; i < 8; i++) {
                exec.submit(() ->
                        rest.getForEntity(base + "/scenarios/s1?delayMs=8000", String.class));
            }
            Thread.sleep(1500);

            DiagnosticReport report = reportService.generateReport();

            assertThat(report.getHypotheses())
                    .extracting(Hypothesis::getId)
                    .contains("H1");
            assertThat(report.getPool()).isNotNull();
            assertThat(report.getPool().getActive()).isGreaterThanOrEqualTo(1);
        } finally {
            exec.shutdownNow();
        }
    }
}