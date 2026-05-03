package com.kuhen.cryptopro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cryptopro.execution")
public class ExecutionProperties {

    private boolean enabled = true;
    private String provider = "paper";
    /** Execution market type: FUTURES or SPOT. */
    private String marketType = "FUTURES";
    private boolean liveEnabled = false;
    private int timeoutMs = 5000;
    private double baseOrderQuantity = 1.0;
    private int maxPartialEntries = 3;
    private double slippageToleranceBps = 6.0;
    private double limitOffsetBps = 1.0;
    private double minSliceQuantity = 0.01;

    /**
     * How long to wait between order-status reconciliation poll attempts (ms).
     * Only applies when using a live exchange provider (e.g. Luno).
     */
    private int reconciliationPollIntervalMs = 500;

    /**
     * Maximum number of poll attempts before giving up on reconciliation and
     * treating the order as having an UNKNOWN fill state.
     */
    private int reconciliationMaxPollAttempts = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getMarketType() {
        return marketType;
    }

    public void setMarketType(String marketType) {
        this.marketType = marketType;
    }

    public boolean isFuturesMarket() {
        return "FUTURES".equalsIgnoreCase(String.valueOf(marketType));
    }

    public boolean isSpotMarket() {
        return "SPOT".equalsIgnoreCase(String.valueOf(marketType));
    }

    public boolean isLiveEnabled() {
        return liveEnabled;
    }

    public void setLiveEnabled(boolean liveEnabled) {
        this.liveEnabled = liveEnabled;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public double getBaseOrderQuantity() {
        return baseOrderQuantity;
    }

    public void setBaseOrderQuantity(double baseOrderQuantity) {
        this.baseOrderQuantity = baseOrderQuantity;
    }

    public int getMaxPartialEntries() {
        return maxPartialEntries;
    }

    public void setMaxPartialEntries(int maxPartialEntries) {
        this.maxPartialEntries = maxPartialEntries;
    }

    public double getSlippageToleranceBps() {
        return slippageToleranceBps;
    }

    public void setSlippageToleranceBps(double slippageToleranceBps) {
        this.slippageToleranceBps = slippageToleranceBps;
    }

    public double getLimitOffsetBps() {
        return limitOffsetBps;
    }

    public void setLimitOffsetBps(double limitOffsetBps) {
        this.limitOffsetBps = limitOffsetBps;
    }

    public double getMinSliceQuantity() {
        return minSliceQuantity;
    }

    public void setMinSliceQuantity(double minSliceQuantity) {
        this.minSliceQuantity = minSliceQuantity;
    }

    public int getReconciliationPollIntervalMs() {
        return reconciliationPollIntervalMs;
    }

    public void setReconciliationPollIntervalMs(int reconciliationPollIntervalMs) {
        this.reconciliationPollIntervalMs = reconciliationPollIntervalMs;
    }

    public int getReconciliationMaxPollAttempts() {
        return reconciliationMaxPollAttempts;
    }

    public void setReconciliationMaxPollAttempts(int reconciliationMaxPollAttempts) {
        this.reconciliationMaxPollAttempts = reconciliationMaxPollAttempts;
    }
}

