package com.kuhen.cryptopro.analytics;

import com.kuhen.cryptopro.ai.MarketRegime;
import com.kuhen.cryptopro.trade.ClosedTrade;
import com.kuhen.cryptopro.trade.TradeCloseReason;

/**
 * Rich context object passed to {@link AnalyticsService} when recording a closed trade.
 * Bundles the trade result with optional market-condition metadata at the time of entry.
 */
public record TradeAnalyticsRecord(
        ClosedTrade closedTrade,
        MarketRegime regime,
        boolean liquiditySweep,
        boolean volumeSpike,
        boolean oiConfirmation
) {
    /** Convenience factory — no condition context (records PnL only). */
    public static TradeAnalyticsRecord of(ClosedTrade trade) {
        return new TradeAnalyticsRecord(trade, null, false, false, false);
    }

    public static TradeAnalyticsRecord of(
            ClosedTrade trade,
            MarketRegime regime,
            boolean liquiditySweep,
            boolean volumeSpike,
            boolean oiConfirmation
    ) {
        return new TradeAnalyticsRecord(trade, regime, liquiditySweep, volumeSpike, oiConfirmation);
    }
}

