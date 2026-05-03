package com.kuhen.cryptopro.ops;

import com.kuhen.cryptopro.ai.MarketRegime;

import java.time.Instant;

public record LatestRegimeResponse(
        String symbol,
        boolean available,
        MarketRegime regime,
        double confidence,
        Instant timestamp,
        String notes
) {
}

