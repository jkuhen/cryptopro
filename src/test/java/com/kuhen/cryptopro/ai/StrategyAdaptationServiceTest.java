package com.kuhen.cryptopro.ai;

import com.kuhen.cryptopro.config.AiProperties;
import com.kuhen.cryptopro.config.StrategyProperties;
import com.kuhen.cryptopro.strategy.SignalDirection;
import com.kuhen.cryptopro.strategy.StrategyDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyAdaptationServiceTest {

    private final StrategyAdaptationService service = new StrategyAdaptationService(new AiProperties(), new StrategyProperties());

    @Test
    void blocksTradingInManipulationWhenConfigured() {
        StrategyDecision baseDecision = new StrategyDecision(0.42, SignalDirection.LONG, 0.9, true, "base");
        SignalScoringResult signal = new SignalScoringResult(0.86, 0.81, "xgboost-baseline", "v1", "notes");
        RegimeDetectionResult regime = new RegimeDetectionResult(MarketRegime.HIGH_VOLATILITY, 0.8, "volatility");

        StrategyAdaptationResult result = service.adapt(baseDecision, signal, regime);

        assertFalse(result.tradable());
        assertTrue(result.sizeMultiplier() == 0.0);
    }
}


