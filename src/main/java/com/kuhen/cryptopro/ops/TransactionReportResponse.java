package com.kuhen.cryptopro.ops;

import java.time.Instant;
import java.util.List;

public record TransactionReportResponse(
        Instant generatedAt,
        int totalTrades,
        int executedToday,
        int closedTrades,
        double winRate,
        double pnlPeriod,
        double grossProfit,
        double grossLoss,
        double netPnl,
        List<TransactionReportRow> rows
) {
}

