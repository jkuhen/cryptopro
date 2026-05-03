package com.kuhen.cryptopro.strategy;

import com.kuhen.cryptopro.ai.MarketRegime;
import com.kuhen.cryptopro.config.StrategyProperties;
import com.kuhen.cryptopro.preprocess.PreprocessingResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StrategyEngine {

    private final TrendAnalyzer trendAnalyzer;
    private final MultiTimeframeBiasService multiTimeframeBiasService;
    private final StrategyProperties strategyProperties;

    /** Default constructor for framework instantiation. */
    public StrategyEngine() {
        this.trendAnalyzer = null;
        this.multiTimeframeBiasService = null;
        this.strategyProperties = null;
    }

    @Autowired
    public StrategyEngine(
            TrendAnalyzer trendAnalyzer,
            MultiTimeframeBiasService multiTimeframeBiasService,
            StrategyProperties strategyProperties
    ) {
        this.trendAnalyzer = trendAnalyzer;
        this.multiTimeframeBiasService = multiTimeframeBiasService;
        this.strategyProperties = strategyProperties;
    }

    public WeightedBiasResult weightedBias(
            PreprocessingResult h1,
            PreprocessingResult m15,
            PreprocessingResult m5
    ) {
        TrendScore h1Score = trendAnalyzer.scoreTrend(h1.candles());
        TrendScore m15Score = trendAnalyzer.scoreTrend(m15.candles());
        TrendScore m5Score = trendAnalyzer.scoreTrend(m5.candles());
        return multiTimeframeBiasService.calculate(h1Score, m15Score, m5Score);
    }

    public StrategyDecision combine(
            WeightedBiasResult bias,
            SmcSignal smcSignal,
            DerivativesSignal derivativesSignal,
            double dataQualityScore
    ) {
        return combine(bias, smcSignal, derivativesSignal, dataQualityScore, MarketRegime.RANGING);
    }

    public StrategyDecision combine(
            WeightedBiasResult bias,
            SmcSignal smcSignal,
            DerivativesSignal derivativesSignal,
            double dataQualityScore,
            MarketRegime regime
    ) {
        StrategyProperties.Weights weights = strategyProperties.getWeights();
        StrategyProperties.Thresholds thresholds = strategyProperties.getThresholds();

        double smcContribution = contextualSmcScore(smcSignal, bias.direction());
        double finalScore = (weights.getFinalBias() * bias.score())
                + (weights.getFinalSmc() * smcContribution)
                + (weights.getFinalDerivatives() * derivativesSignal.score());
        finalScore *= regimeMultiplier(regime);

        boolean riskBlocked = dataQualityScore < thresholds.getQualityHardBlock();
        boolean tradable = bias.allowEntry() && !riskBlocked;
        double adjustedSize = tradable ? bias.sizeMultiplier() * Math.max(0.3, dataQualityScore) : 0.0;

        SignalDirection direction = SignalDirection.NEUTRAL;
        if (tradable) {
            double directionCutoff = thresholds.getDecisionDirectionCutoff();
            direction = finalScore > directionCutoff ? SignalDirection.LONG : finalScore < -directionCutoff ? SignalDirection.SHORT : SignalDirection.NEUTRAL;
        }

        String rationale = "bias=" + round(bias.score())
                + ", smc=" + round(smcContribution)
                + " [bos=" + patternSummary(smcSignal.breakOfStructureSignal())
                + ", sweep=" + patternSummary(smcSignal.liquiditySweepSignal())
                + ", fvg=" + patternSummary(smcSignal.fairValueGapSignal()) + "]"
                + ", derivatives=" + round(derivativesSignal.score())
                + ", regime=" + regime
                + ", quality=" + round(dataQualityScore)
                + (riskBlocked ? " (risk block: low data quality)" : "");

        return new StrategyDecision(finalScore, direction, adjustedSize, tradable && direction != SignalDirection.NEUTRAL, rationale, regime);
    }

    private double regimeMultiplier(MarketRegime regime) {
        return switch (regime) {
            case TRENDING -> 1.05;
            case RANGING -> 0.95;
            case HIGH_VOLATILITY, HIGH_MANIPULATION -> 0.80;
        };
    }

    private double contextualSmcScore(SmcSignal smcSignal, SignalDirection biasDirection) {
        if (biasDirection == SignalDirection.NEUTRAL || smcSignal.direction() == SignalDirection.NEUTRAL) {
            return smcSignal.score() * 0.35;
        }

        if (biasDirection == smcSignal.direction()) {
            return smcSignal.score() * 1.10;
        }

        return smcSignal.score() * 0.85;
    }

    private String patternSummary(PriceActionPatternSignal pattern) {
        if (!pattern.detected()) {
            return "none";
        }
        return pattern.direction() + "@" + round(pattern.strength());
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}

