package com.kuhen.cryptopro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cryptopro.paper-portfolio")
public class PaperPortfolioProperties {

    private double initialCashUsd = 10_000.0;
    private double feeRate = 0.001;
    private int maxEquityPoints = 500;

    public double getInitialCashUsd() {
        return initialCashUsd;
    }

    public void setInitialCashUsd(double initialCashUsd) {
        this.initialCashUsd = initialCashUsd;
    }

    public double getFeeRate() {
        return feeRate;
    }

    public void setFeeRate(double feeRate) {
        this.feeRate = feeRate;
    }

    public int getMaxEquityPoints() {
        return maxEquityPoints;
    }

    public void setMaxEquityPoints(int maxEquityPoints) {
        this.maxEquityPoints = maxEquityPoints;
    }
}

