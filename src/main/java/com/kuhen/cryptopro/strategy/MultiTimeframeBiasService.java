package com.kuhen.cryptopro.strategy;

import com.kuhen.cryptopro.config.StrategyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MultiTimeframeBiasService {

    private final StrategyProperties strategyProperties;

    /** Default constructor for framework instantiation. */
    public MultiTimeframeBiasService() {
        this.strategyProperties = null;
    }

    @Autowired
    public MultiTimeframeBiasService(StrategyProperties strategyProperties) {
        this.strategyProperties = strategyProperties;
    }

    public WeightedBiasResult calculate(TrendScore h1, TrendScore m15, TrendScore m5) {
        StrategyProperties.Weights weights = strategyProperties.getWeights();
        StrategyProperties.Thresholds thresholds = strategyProperties.getThresholds();

        double weightedScore = (weights.getH1() * h1.score()) + (weights.getM15() * m15.score()) + (weights.getM5() * m5.score());
        double disagreement = disagreementIndex(h1, m15, m5);

        boolean allowEntry = Math.abs(weightedScore) >= thresholds.getEntryThreshold();
        double sizeMultiplier = Math.max(thresholds.getMinSizeMultiplier(), 1.0 - (thresholds.getDisagreementPenalty() * disagreement));

        SignalDirection direction = SignalDirection.NEUTRAL;
        if (weightedScore > thresholds.getDirectionThreshold()) {
            direction = SignalDirection.LONG;
        } else if (weightedScore < -thresholds.getDirectionThreshold()) {
            direction = SignalDirection.SHORT;
        }

        return new WeightedBiasResult(weightedScore, direction, disagreement, allowEntry, sizeMultiplier);
    }

    private double disagreementIndex(TrendScore h1, TrendScore m15, TrendScore m5) {
        double mismatch = 0.0;
        mismatch += isOpposite(h1, m15) ? 0.30 : 0.0;
        mismatch += isOpposite(h1, m5) ? 0.25 : 0.0;
        mismatch += isOpposite(m15, m5) ? 0.20 : 0.0;
        return Math.min(1.0, mismatch);
    }

    private boolean isOpposite(TrendScore left, TrendScore right) {
        if (left.direction() == SignalDirection.NEUTRAL || right.direction() == SignalDirection.NEUTRAL) {
            return false;
        }
        return left.direction() != right.direction();
    }
}

