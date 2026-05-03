package com.kuhen.cryptopro.analytics;

import java.util.List;

/**
 * Performance breakdown slice for one condition, regime, direction, or close reason.
 */
public record AnalyticsBreakdown(
        String label,
        int totalTrades,
        int wins,
        int losses,
        double winRatePercent,
        double totalPnl,
        double profitFactor
) {
}

