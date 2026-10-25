package com.txscope.starter;

import com.txscope.core.model.pool.DiagnosticReport;
import com.txscope.core.report.ReportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint that returns the current TxScope diagnostic report as JSON.
 */
@RestController
@RequestMapping("/internal/txscope")
public class TxScopeReportController {

    private final ReportService reportService;

    public TxScopeReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping(value = "/report", produces = MediaType.APPLICATION_JSON_VALUE)
    public DiagnosticReport report() {
        return reportService.generateReport();
    }
}
