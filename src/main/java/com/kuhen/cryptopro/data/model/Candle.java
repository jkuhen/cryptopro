package com.kuhen.cryptopro.data.model;

import java.time.Instant;

public record Candle(
        String symbol,
        Timeframe timeframe,
        Instant openTime,
        double open,
        double high,
        double low,
        double close,
        double volume
) {
}

