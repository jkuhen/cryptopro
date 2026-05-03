package com.kuhen.cryptopro.strategy;

import com.kuhen.cryptopro.ai.MarketRegime;

public record StrategyDecision(
        double finalScore,
        SignalDirection direction,
        double sizeMultiplier,
        boolean tradable,
        String rationale,
        MarketRegime regime
) {
    public StrategyDecision(
            double finalScore,
            SignalDirection direction,
            double sizeMultiplier,
            boolean tradable,
            String rationale
    ) {
        this(finalScore, direction, sizeMultiplier, tradable, rationale, MarketRegime.RANGING);
    }
}

