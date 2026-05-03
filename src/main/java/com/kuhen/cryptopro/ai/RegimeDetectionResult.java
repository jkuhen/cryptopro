package com.kuhen.cryptopro.ai;

public record RegimeDetectionResult(
        MarketRegime regime,
        double confidence,
        String notes
) {
}

