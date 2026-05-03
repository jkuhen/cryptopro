package com.kuhen.cryptopro.ops;

import java.time.Instant;

public record TransactionReportRow(
        Instant closedAt,
        Instant executedAt,
        String symbol,
        String action,
        Double executedPrice,
        Double quantity,
        String outcome,
        Double pnl,
        String accountId,
        String provider,
        String source
) {
}

