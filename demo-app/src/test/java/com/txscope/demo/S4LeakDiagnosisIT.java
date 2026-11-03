package com.txscope.demo;

import com.txscope.core.model.Hypothesis;
import com.txscope.core.model.pool.DiagnosticReport;
import com.txscope.core.report.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class S4LeakDiagnosisIT extends AbstractTxScopeIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ReportService reportService;

    @AfterEach
    void cleanup() {
        try {
            rest.exchange("http://localhost:" + port + "/scenarios/s4/release",
                    HttpMethod.POST, null, String.class);
        } catch (Exception ignored) {
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void s4_shouldProduceH4() {
        String base = "http://localhost:" + port;

        // reset if endpoint exists
        try {
            rest.exchange(base + "/scenarios/s4/release", HttpMethod.POST, null, String.class);
        } catch (Exception ignored) {
        }

        for (int i = 0; i < 6; i++) {
            try {
                rest.exchange(base + "/scenarios/s4/leak", HttpMethod.POST, null, String.class);
            } catch (Exception ignored) {
                // pool may reject last leaks
            }
        }

        DiagnosticReport report = reportService.generateReport();

        assertThat(report.getPool()).isNotNull();
        assertThat(report.getPool().isFullyActive() || report.getPool().getActive() >= 4).isTrue();
        assertThat(report.getHypotheses())
                .extracting(Hypothesis::getId)
                .contains("H4");
    }
}
