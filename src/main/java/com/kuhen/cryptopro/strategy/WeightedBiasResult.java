package com.kuhen.cryptopro.strategy;

public record WeightedBiasResult(
        double score,
        SignalDirection direction,
        double disagreementIndex,
        boolean allowEntry,
        double sizeMultiplier
) {
}

