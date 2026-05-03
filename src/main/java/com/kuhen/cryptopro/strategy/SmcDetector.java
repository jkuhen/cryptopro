package com.kuhen.cryptopro.strategy;

import com.kuhen.cryptopro.config.StrategyProperties;
import com.kuhen.cryptopro.preprocess.PreprocessedCandle;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SmcDetector {

    private final PriceActionPatternDetector priceActionPatternDetector;
    private final StrategyProperties strategyProperties;

    public SmcDetector(PriceActionPatternDetector priceActionPatternDetector, StrategyProperties strategyProperties) {
        this.priceActionPatternDetector = priceActionPatternDetector;
        this.strategyProperties = strategyProperties;
    }

    public SmcSignal detect(List<PreprocessedCandle> candles) {
        if (candles == null || candles.size() < 5) {
            return new SmcSignal(
                    PriceActionPatternSignal.none("Insufficient candles for liquidity sweep"),
                    PriceActionPatternSignal.none("Insufficient candles for BOS"),
                    PriceActionPatternSignal.none("Insufficient candles for FVG"),
                    0.0,
                    SignalDirection.NEUTRAL,
                    "Insufficient candles for SMC detection"
            );
        }

        PriceActionPatternDetector.PatternSet patterns = priceActionPatternDetector.detect(candles);
        double score = patternScore(patterns.bos(), patterns.liquiditySweep(), patterns.fairValueGap());
        SignalDirection direction = score > 0.0 ? SignalDirection.LONG : score < 0.0 ? SignalDirection.SHORT : SignalDirection.NEUTRAL;
        String notes = "SMC stack: bos=" + describe(patterns.bos())
                + ", sweep=" + describe(patterns.liquiditySweep())
                + ", fvg=" + describe(patterns.fairValueGap());

        return new SmcSignal(patterns.liquiditySweep(), patterns.bos(), patterns.fairValueGap(), score, direction, notes);
    }

    private double patternScore(
            PriceActionPatternSignal bos,
            PriceActionPatternSignal sweep,
            PriceActionPatternSignal fvg
    ) {
        StrategyProperties.Smc smc = strategyProperties.getSmc();
        return contribution(bos, smc.getBosWeight())
                + contribution(sweep, smc.getLiquiditySweepWeight())
                + contribution(fvg, smc.getFairValueGapWeight());
    }

    private double contribution(PriceActionPatternSignal pattern, double weight) {
        if (!pattern.detected() || pattern.direction() == SignalDirection.NEUTRAL) {
            return 0.0;
        }
        double directionalStrength = pattern.direction() == SignalDirection.LONG ? pattern.strength() : -pattern.strength();
        return weight * directionalStrength;
    }

    private String describe(PriceActionPatternSignal pattern) {
        if (!pattern.detected()) {
            return "none";
        }
        return pattern.direction() + "@" + Math.round(pattern.strength() * 1000.0) / 1000.0;
    }
}

