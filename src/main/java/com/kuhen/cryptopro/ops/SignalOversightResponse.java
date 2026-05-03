package com.kuhen.cryptopro.ops;

import java.time.Instant;
import java.util.List;

/**
 * Response payload for the signal oversight report endpoint.
 * Provides open signals, missed trades, and aggregate telemetry.
 */
public record SignalOversightResponse(
        List<OpenSignalView> openSignals,
        List<MissedTradeView> missedTrades,
        int openSignalCount,
        int missedTradeCount,
        int rejectedSignalCount,
        List<RejectReasonCount> topRejectReasons,
        double aiConfidenceRejectPct,
        int aiConfidenceRejectCount
) {

    /**
     * A single open (pending) signal entry shown in the Open Signals card.
     */
    public record OpenSignalView(
            Instant createdAt,
            String symbol,
            String action,
            Double confidence,
            Double entryPrice,
            Double stopLoss,
            Double takeProfit,
            long ageMinutes,
            String executionHint,
            String executionDetail
    ) {}

    /**
     * A single missed trade entry shown in the Missed Trades card.
     */
    public record MissedTradeView(
            Instant createdAt,
            String symbol,
            String timeframeCode,
            String action,
            Double confidence,
            Double entryPrice,
            String signalStatus,
            String reason,
            String detailedReason
    ) {}

    /**
     * Top reject reason aggregate (reason label + occurrences).
     */
    public record RejectReasonCount(String reason, int count) {}
}

