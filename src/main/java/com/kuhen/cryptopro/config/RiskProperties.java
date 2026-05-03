package com.kuhen.cryptopro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cryptopro.risk")
public class RiskProperties {

    private double maxSpreadBps = 8.0;
    private long maxLatencyMs = 1500;
    private long staleFeedSeconds = 20;
    private double accountEquityUsd = 10_000.0;
    private double riskPerTradePercent = 1.0;
    private double dailyLossCapPercent = 5.0;
    private int maxConcurrentTrades = 3;
    private int atrPeriod = 14;
    private double atrStopMultiplier = 2.0;

    public double getMaxSpreadBps() {
        return maxSpreadBps;
    }

    public void setMaxSpreadBps(double maxSpreadBps) {
        this.maxSpreadBps = maxSpreadBps;
    }

    public long getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public void setMaxLatencyMs(long maxLatencyMs) {
        this.maxLatencyMs = maxLatencyMs;
    }

    public long getStaleFeedSeconds() {
        return staleFeedSeconds;
    }

    public void setStaleFeedSeconds(long staleFeedSeconds) {
        this.staleFeedSeconds = staleFeedSeconds;
    }

    public double getAccountEquityUsd() {
        return accountEquityUsd;
    }

    public void setAccountEquityUsd(double accountEquityUsd) {
        this.accountEquityUsd = accountEquityUsd;
    }

    public double getRiskPerTradePercent() {
        return riskPerTradePercent;
    }

    public void setRiskPerTradePercent(double riskPerTradePercent) {
        this.riskPerTradePercent = riskPerTradePercent;
    }

    public double getDailyLossCapPercent() {
        return dailyLossCapPercent;
    }

    public void setDailyLossCapPercent(double dailyLossCapPercent) {
        this.dailyLossCapPercent = dailyLossCapPercent;
    }

    public int getMaxConcurrentTrades() {
        return maxConcurrentTrades;
    }

    public void setMaxConcurrentTrades(int maxConcurrentTrades) {
        this.maxConcurrentTrades = maxConcurrentTrades;
    }

    public int getAtrPeriod() {
        return atrPeriod;
    }

    public void setAtrPeriod(int atrPeriod) {
        this.atrPeriod = atrPeriod;
    }

    public double getAtrStopMultiplier() {
        return atrStopMultiplier;
    }

    public void setAtrStopMultiplier(double atrStopMultiplier) {
        this.atrStopMultiplier = atrStopMultiplier;
    }
}

