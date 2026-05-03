package com.kuhen.cryptopro.data.model;

import java.time.Instant;
import java.util.List;

public record OrderBookSnapshot(
        String symbol,
        Instant snapshotTime,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks
) {
    public double spread() {
        if (bids.isEmpty() || asks.isEmpty()) {
            return 0.0;
        }
        return asks.get(0).price() - bids.get(0).price();
    }
}

