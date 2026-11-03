package com.txscope.demo;

import com.txscope.core.model.Hypothesis;
import com.txscope.core.model.pool.DiagnosticReport;
import com.txscope.core.report.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestRestTemplate
class S2LockDiagnosisIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("txscope")
            .withUsername("txscope")
            .withPassword("txscope");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("txscope.monitor.jdbc-url", postgres::getJdbcUrl);
        registry.add("txscope.monitor.username", postgres::getUsername);
        registry.add("txscope.monitor.password", postgres::getPassword);
        registry.add("txscope.report-enabled", () -> "true");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "2");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ReportService reportService;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void s2_lockContention_shouldProduceH2() throws Exception {
        String base = "http://localhost:" + port;

        ExecutorService exec = Executors.newFixedThreadPool(6);
        try {
            // Holder: keeps advisory lock ~15s
            Future<?> holder = exec.submit(() ->
                    rest.getForEntity(base + "/scenarios/s2/hold?holdMs=15000", String.class));

            Thread.sleep(800);

            // Waiters: block on same lock
            for (int i = 0; i < 4; i++) {
                exec.submit(() ->
                        rest.getForEntity(base + "/scenarios/s2/wait?timeoutHintMs=1000", String.class));
            }

            Thread.sleep(2000);

            DiagnosticReport report = reportService.generateReport();

            assertThat(report.getPool()).isNotNull();
            assertThat(report.getPool().getActive()).isGreaterThanOrEqualTo(1);

            assertThat(report.getHypotheses())
                    .extracting(Hypothesis::getId)
                    .contains("H2");

            assertThat(report.getHypotheses())
                    .filteredOn(h -> "H2".equals(h.getId()))
                    .isNotEmpty();

            // Prefer not blaming pure S1 when lock pattern is present
            // (H1 may be absent — soft check)
            boolean hasH2 = report.getHypotheses().stream().anyMatch(h -> "H2".equals(h.getId()));
            assertThat(hasH2).isTrue();

            holder.get(20, TimeUnit.SECONDS);
        } finally {
            exec.shutdownNow();
        }
    }
}
