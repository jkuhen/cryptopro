package com.kuhen.cryptopro.ai;

public record SignalScoringResult(
        double probabilityOfSuccess,
        double confidence,
        String modelName,
        String modelVersion,
        String notes
) {
}


