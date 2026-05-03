package com.kuhen.cryptopro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cryptopro.trade-lifecycle")
public class TradeLifecycleProperties {

    /** ATR multiplier used to compute the trailing stop distance. */
    private double trailingStopAtrMultiplier = 2.0;

    /**
     * ATR multiplier used to compute an automatic take-profit price when
     * opening a trade.  Set to 0 to disable automatic take-profit.
     */
    private double takeProfitAtrMultiplier = 3.0;

    /** Enables or disables the lifecycle manager globally. */
    private boolean enabled = true;

    /**
     * When true, a signal reversal (new direction opposing the open trade)
     * will trigger an automatic close.
     */
    private boolean closeOnSignalReversal = true;

    public double getTrailingStopAtrMultiplier() { return trailingStopAtrMultiplier; }
    public void setTrailingStopAtrMultiplier(double v) { this.trailingStopAtrMultiplier = v; }

    public double getTakeProfitAtrMultiplier() { return takeProfitAtrMultiplier; }
    public void setTakeProfitAtrMultiplier(double v) { this.takeProfitAtrMultiplier = v; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }

    public boolean isCloseOnSignalReversal() { return closeOnSignalReversal; }
    public void setCloseOnSignalReversal(boolean v) { this.closeOnSignalReversal = v; }
}

