package com.kuhen.cryptopro.analytics;

import java.time.Instant;
import java.util.List;

/**
 * Complete analytics summary for a given symbol and time window.
 */
public record AnalyticsSummary(
        String symbol,
        Instant from,
        Instant to,
        int totalTrades,
        int wins,
        int losses,
        double winRatePercent,
        double grossProfit,
        double grossLoss,
        double profitFactor,
        double totalPnl,
        double maxDrawdown,
        double maxDrawdownPercent,
        double averageWin,
        double averageLoss,
        long averageHoldDurationSec,
        List<AnalyticsBreakdown> byRegime,
        List<AnalyticsBreakdown> byCondition,
        List<AnalyticsBreakdown> byCloseReason,
        List<AnalyticsBreakdown> byDirection
) {
}

