package com.kuhen.cryptopro.strategy;

import com.kuhen.cryptopro.preprocess.PreprocessedCandle;
import com.kuhen.cryptopro.config.StrategyProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TrendAnalyzer {

    private final StrategyProperties strategyProperties;

    public TrendAnalyzer(StrategyProperties strategyProperties) {
        this.strategyProperties = strategyProperties;
    }

    public TrendScore scoreTrend(List<PreprocessedCandle> candles) {
        if (candles.size() < 2) {
            return new TrendScore(SignalDirection.NEUTRAL, 0.0);
        }

        double first = candles.get(0).cleanClose();
        double last = candles.get(candles.size() - 1).cleanClose();
        if (first <= 0.0) {
            return new TrendScore(SignalDirection.NEUTRAL, 0.0);
        }

        double move = (last - first) / first;
        double scaleDenominator = Math.max(strategyProperties.getThresholds().getTrendScaleDenominator(), 0.0001);
        double scaled = Math.max(-1.0, Math.min(1.0, move / scaleDenominator));
        double directionCutoff = strategyProperties.getThresholds().getTrendDirectionCutoff();

        if (scaled > directionCutoff) {
            return new TrendScore(SignalDirection.LONG, scaled);
        }
        if (scaled < -directionCutoff) {
            return new TrendScore(SignalDirection.SHORT, scaled);
        }
        return new TrendScore(SignalDirection.NEUTRAL, scaled * strategyProperties.getThresholds().getTrendNeutralDampening());
    }
}

