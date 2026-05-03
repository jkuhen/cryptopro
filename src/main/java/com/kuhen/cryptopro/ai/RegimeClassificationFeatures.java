package com.kuhen.cryptopro.ai;

public record RegimeClassificationFeatures(
        double h1TrendSlope,
        double m15TrendSlope,
        double realizedVolatility,
        double atrPercent,
        double rangeCompression,
        double volumeSpike,
        double openInterestChange,
        double fundingRate
) {
}

