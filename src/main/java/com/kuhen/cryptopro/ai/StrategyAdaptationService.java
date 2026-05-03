package com.kuhen.cryptopro.ai;

import com.kuhen.cryptopro.config.AiProperties;
import com.kuhen.cryptopro.config.StrategyProperties;
import com.kuhen.cryptopro.strategy.StrategyDecision;
import org.springframework.stereotype.Service;

@Service
public class StrategyAdaptationService {

    private final AiProperties aiProperties;
    private final StrategyProperties strategyProperties;

    public StrategyAdaptationService(AiProperties aiProperties, StrategyProperties strategyProperties) {
        this.aiProperties = aiProperties;
        this.strategyProperties = strategyProperties;
    }

    public StrategyAdaptationResult adapt(
            StrategyDecision baseDecision,
            SignalScoringResult signalScoringResult,
            RegimeDetectionResult regimeDetectionResult
    ) {
        AiProperties.Adaptation adaptation = aiProperties.getAdaptation();
        AiProperties.Thresholds thresholds = aiProperties.getThresholds();

        double regimeSizeMultiplier;
        double thresholdMultiplier;
        switch (regimeDetectionResult.regime()) {
            case TRENDING -> {
                regimeSizeMultiplier = adaptation.getTrendingSizeMultiplier();
                thresholdMultiplier = adaptation.getTrendingDirectionThresholdMultiplier();
            }
            case RANGING -> {
                regimeSizeMultiplier = adaptation.getRangingSizeMultiplier();
                thresholdMultiplier = adaptation.getRangingDirectionThresholdMultiplier();
            }
            case HIGH_VOLATILITY, HIGH_MANIPULATION -> {
                regimeSizeMultiplier = adaptation.getManipulationSizeMultiplier();
                thresholdMultiplier = adaptation.getManipulationDirectionThresholdMultiplier();
            }
            default -> {
                regimeSizeMultiplier = 1.0;
                thresholdMultiplier = 1.0;
            }
        }

        boolean aiQualityOk = signalScoringResult.probabilityOfSuccess() >= thresholds.getMinProbability()
                && signalScoringResult.confidence() >= thresholds.getMinConfidence();

        boolean manipulationBlocked = (regimeDetectionResult.regime() == MarketRegime.HIGH_VOLATILITY
                || regimeDetectionResult.regime() == MarketRegime.HIGH_MANIPULATION)
                && adaptation.isBlockTradingInManipulation();

        boolean tradable = baseDecision.tradable() && aiQualityOk && !manipulationBlocked;
        double aiConfidenceSizing = Math.max(0.25, signalScoringResult.confidence());
        double adjustedSize = tradable
                ? baseDecision.sizeMultiplier() * regimeSizeMultiplier * aiConfidenceSizing
                : 0.0;

        double adjustedDirectionThreshold = strategyProperties.getThresholds().getDecisionDirectionCutoff() * thresholdMultiplier;

        String notes = "aiProb=" + round(signalScoringResult.probabilityOfSuccess())
                + ", aiConf=" + round(signalScoringResult.confidence())
                + ", regime=" + regimeDetectionResult.regime()
                + (manipulationBlocked ? " (blocked in manipulation regime)" : "");

        return new StrategyAdaptationResult(tradable, adjustedSize, adjustedDirectionThreshold, notes);
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}

