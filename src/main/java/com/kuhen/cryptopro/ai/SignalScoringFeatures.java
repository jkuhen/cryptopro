package com.kuhen.cryptopro.ai;

public record SignalScoringFeatures(
        double trendScore,
        double rsi,
        double volumeSpike,
        double openInterestChange,
        double fundingRate
) {
}

