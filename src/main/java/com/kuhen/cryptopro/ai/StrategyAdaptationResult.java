package com.kuhen.cryptopro.ai;

public record StrategyAdaptationResult(
        boolean tradable,
        double sizeMultiplier,
        double adjustedDirectionThreshold,
        String notes
) {
}

