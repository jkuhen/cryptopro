package com.kuhen.cryptopro.ops;

import java.time.Instant;

public record TransactionLogEntry(
        Instant timestamp,
        String source,
        String symbol,
        String direction,
        String status,
        double requestedQuantity,
        double filledQuantity,
        double slippageBps,
        String notes,
        String accountId,
        String provider,
        Double executedPrice
) {

    public TransactionLogEntry(
            Instant timestamp,
            String source,
            String symbol,
            String direction,
            String status,
            double requestedQuantity,
            double filledQuantity,
            double slippageBps,
            String notes
    ) {
        this(timestamp, source, symbol, direction, status, requestedQuantity, filledQuantity,
                slippageBps, notes, null, null, null);
    }
}

