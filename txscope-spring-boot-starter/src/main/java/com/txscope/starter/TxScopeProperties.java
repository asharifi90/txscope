package com.txscope.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "txscope")
public class TxScopeProperties {

    /**
     * When false, the HTTP report endpoint is not registered.
     * Default false = safe for production-like setups.
     */
    private boolean reportEnabled = false;

    public boolean isReportEnabled() {
        return reportEnabled;
    }

    public void setReportEnabled(boolean reportEnabled) {
        this.reportEnabled = reportEnabled;
    }
}
