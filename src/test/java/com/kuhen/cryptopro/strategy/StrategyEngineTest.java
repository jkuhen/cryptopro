package com.kuhen.cryptopro.strategy;

import com.kuhen.cryptopro.ai.MarketRegime;
import com.kuhen.cryptopro.config.StrategyProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyEngineTest {

    private final StrategyProperties properties = new StrategyProperties();
    private final StrategyEngine engine = new StrategyEngine(
            new TrendAnalyzer(properties),
            new MultiTimeframeBiasService(properties),
            properties
    );

    @Test
    void alignedBosSweepAndFvgIncreaseStrategyConviction() {
        WeightedBiasResult bias = new WeightedBiasResult(0.62, SignalDirection.LONG, 0.05, true, 1.0);
        DerivativesSignal derivatives = new DerivativesSignal(SignalDirection.NEUTRAL, 0.0, 0.0, 0.0, 0.0, "flat");

        StrategyDecision decision = engine.combine(
                bias,
                bullishSmcSignal(),
                derivatives,
                0.95
        );

        assertTrue(decision.tradable());
        assertEquals(SignalDirection.LONG, decision.direction());
        assertTrue(decision.finalScore() > 0.45);
        assertTrue(decision.rationale().contains("bos=LONG@"));
        assertTrue(decision.rationale().contains("sweep=LONG@"));
        assertTrue(decision.rationale().contains("fvg=LONG@"));
    }

    @Test
    void bearishPriceActionFlagsReduceBullishBiasScore() {
        WeightedBiasResult bias = new WeightedBiasResult(0.62, SignalDirection.LONG, 0.05, true, 1.0);
        DerivativesSignal derivatives = new DerivativesSignal(SignalDirection.NEUTRAL, 0.0, 0.0, 0.0, 0.0, "flat");

        StrategyDecision aligned = engine.combine(bias, bullishSmcSignal(), derivatives, 0.95);
        StrategyDecision counterTrend = engine.combine(bias, bearishSmcSignal(), derivatives, 0.95);

        assertTrue(aligned.finalScore() > counterTrend.finalScore());
        assertTrue(counterTrend.finalScore() < 0.31);
        assertTrue(counterTrend.rationale().contains("sweep=SHORT@"));
    }

    @Test
    void highVolatilityRegimeDampensFinalScore() {
        WeightedBiasResult bias = new WeightedBiasResult(0.62, SignalDirection.LONG, 0.05, true, 1.0);
        DerivativesSignal derivatives = new DerivativesSignal(SignalDirection.NEUTRAL, 0.0, 0.0, 0.0, 0.0, "flat");

        StrategyDecision trending = engine.combine(bias, bullishSmcSignal(), derivatives, 0.95, MarketRegime.TRENDING);
        StrategyDecision highVol = engine.combine(bias, bullishSmcSignal(), derivatives, 0.95, MarketRegime.HIGH_VOLATILITY);

        assertTrue(trending.finalScore() > highVol.finalScore());
        assertEquals(MarketRegime.HIGH_VOLATILITY, highVol.regime());
    }

    private SmcSignal bullishSmcSignal() {
        return new SmcSignal(
                new PriceActionPatternSignal(true, SignalDirection.LONG, 0.70, 99.8, 100.9, "bullish sweep"),
                new PriceActionPatternSignal(true, SignalDirection.LONG, 0.85, 101.2, 103.0, "bullish bos"),
                new PriceActionPatternSignal(true, SignalDirection.LONG, 0.60, 100.5, 101.0, "bullish fvg"),
                0.74,
                SignalDirection.LONG,
                "bullish price action stack"
        );
    }

    private SmcSignal bearishSmcSignal() {
        return new SmcSignal(
                new PriceActionPatternSignal(true, SignalDirection.SHORT, 0.65, 104.0, 102.8, "bearish sweep"),
                new PriceActionPatternSignal(false, SignalDirection.NEUTRAL, 0.0, 0.0, 0.0, "no bos"),
                new PriceActionPatternSignal(true, SignalDirection.SHORT, 0.55, 103.7, 103.1, "bearish fvg"),
                -0.50,
                SignalDirection.SHORT,
                "bearish price action stack"
        );
    }
}

