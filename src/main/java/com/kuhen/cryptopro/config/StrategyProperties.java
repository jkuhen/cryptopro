package com.kuhen.cryptopro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cryptopro.strategy")
public class StrategyProperties {

    private final Weights weights = new Weights();
    private final Thresholds thresholds = new Thresholds();
    private final Smc smc = new Smc();
    private final Signal signal = new Signal();

    public Weights getWeights() {
        return weights;
    }

    public Thresholds getThresholds() {
        return thresholds;
    }

    public Smc getSmc() {
        return smc;
    }

    public Signal getSignal() {
        return signal;
    }

    public static class Weights {
        private double h1 = 0.50;
        private double m15 = 0.30;
        private double m5 = 0.20;
        private double finalBias = 0.50;
        private double finalSmc = 0.30;
        private double finalDerivatives = 0.20;

        public double getH1() {
            return h1;
        }

        public void setH1(double h1) {
            this.h1 = h1;
        }

        public double getM15() {
            return m15;
        }

        public void setM15(double m15) {
            this.m15 = m15;
        }

        public double getM5() {
            return m5;
        }

        public void setM5(double m5) {
            this.m5 = m5;
        }

        public double getFinalBias() {
            return finalBias;
        }

        public void setFinalBias(double finalBias) {
            this.finalBias = finalBias;
        }

        public double getFinalSmc() {
            return finalSmc;
        }

        public void setFinalSmc(double finalSmc) {
            this.finalSmc = finalSmc;
        }

        public double getFinalDerivatives() {
            return finalDerivatives;
        }

        public void setFinalDerivatives(double finalDerivatives) {
            this.finalDerivatives = finalDerivatives;
        }
    }

    public static class Thresholds {
        private double entryThreshold = 0.18;
        private double directionThreshold = 0.05;
        private double disagreementPenalty = 0.60;
        private double minSizeMultiplier = 0.25;
        private double trendScaleDenominator = 0.02;
        private double trendDirectionCutoff = 0.08;
        private double trendNeutralDampening = 0.50;
        private double fundingExtremeRate = 0.01;
        private double derivativesDirectionCutoff = 0.12;
        private double derivativesImbalanceScale = 0.20;
        private double derivativesImbalanceCap = 0.20;
        private double qualityHardBlock = 0.35;
        private double decisionDirectionCutoff = 0.10;
        private double oiPriceUpScore = 0.60;
        private double oiPriceDownScore = -0.60;

        public double getEntryThreshold() {
            return entryThreshold;
        }

        public void setEntryThreshold(double entryThreshold) {
            this.entryThreshold = entryThreshold;
        }

        public double getDirectionThreshold() {
            return directionThreshold;
        }

        public void setDirectionThreshold(double directionThreshold) {
            this.directionThreshold = directionThreshold;
        }

        public double getDisagreementPenalty() {
            return disagreementPenalty;
        }

        public void setDisagreementPenalty(double disagreementPenalty) {
            this.disagreementPenalty = disagreementPenalty;
        }

        public double getMinSizeMultiplier() {
            return minSizeMultiplier;
        }

        public void setMinSizeMultiplier(double minSizeMultiplier) {
            this.minSizeMultiplier = minSizeMultiplier;
        }

        public double getTrendScaleDenominator() {
            return trendScaleDenominator;
        }

        public void setTrendScaleDenominator(double trendScaleDenominator) {
            this.trendScaleDenominator = trendScaleDenominator;
        }

        public double getTrendDirectionCutoff() {
            return trendDirectionCutoff;
        }

        public void setTrendDirectionCutoff(double trendDirectionCutoff) {
            this.trendDirectionCutoff = trendDirectionCutoff;
        }

        public double getTrendNeutralDampening() {
            return trendNeutralDampening;
        }

        public void setTrendNeutralDampening(double trendNeutralDampening) {
            this.trendNeutralDampening = trendNeutralDampening;
        }

        public double getFundingExtremeRate() {
            return fundingExtremeRate;
        }

        public void setFundingExtremeRate(double fundingExtremeRate) {
            this.fundingExtremeRate = fundingExtremeRate;
        }

        public double getDerivativesDirectionCutoff() {
            return derivativesDirectionCutoff;
        }

        public void setDerivativesDirectionCutoff(double derivativesDirectionCutoff) {
            this.derivativesDirectionCutoff = derivativesDirectionCutoff;
        }

        public double getDerivativesImbalanceScale() {
            return derivativesImbalanceScale;
        }

        public void setDerivativesImbalanceScale(double derivativesImbalanceScale) {
            this.derivativesImbalanceScale = derivativesImbalanceScale;
        }

        public double getDerivativesImbalanceCap() {
            return derivativesImbalanceCap;
        }

        public void setDerivativesImbalanceCap(double derivativesImbalanceCap) {
            this.derivativesImbalanceCap = derivativesImbalanceCap;
        }

        public double getQualityHardBlock() {
            return qualityHardBlock;
        }

        public void setQualityHardBlock(double qualityHardBlock) {
            this.qualityHardBlock = qualityHardBlock;
        }

        public double getDecisionDirectionCutoff() {
            return decisionDirectionCutoff;
        }

        public void setDecisionDirectionCutoff(double decisionDirectionCutoff) {
            this.decisionDirectionCutoff = decisionDirectionCutoff;
        }

        public double getOiPriceUpScore() {
            return oiPriceUpScore;
        }

        public void setOiPriceUpScore(double oiPriceUpScore) {
            this.oiPriceUpScore = oiPriceUpScore;
        }

        public double getOiPriceDownScore() {
            return oiPriceDownScore;
        }

        public void setOiPriceDownScore(double oiPriceDownScore) {
            this.oiPriceDownScore = oiPriceDownScore;
        }
    }

    public static class Smc {
        private int lookbackCandles = 24;
        private double bosCloseBufferPercent = 0.0005;
        private double sweepBufferPercent = 0.0003;
        private double minFairValueGapPercent = 0.0008;
        private double bosWeight = 0.40;
        private double liquiditySweepWeight = 0.35;
        private double fairValueGapWeight = 0.25;

        public int getLookbackCandles() {
            return lookbackCandles;
        }

        public void setLookbackCandles(int lookbackCandles) {
            this.lookbackCandles = lookbackCandles;
        }

        public double getBosCloseBufferPercent() {
            return bosCloseBufferPercent;
        }

        public void setBosCloseBufferPercent(double bosCloseBufferPercent) {
            this.bosCloseBufferPercent = bosCloseBufferPercent;
        }

        public double getSweepBufferPercent() {
            return sweepBufferPercent;
        }

        public void setSweepBufferPercent(double sweepBufferPercent) {
            this.sweepBufferPercent = sweepBufferPercent;
        }

        public double getMinFairValueGapPercent() {
            return minFairValueGapPercent;
        }

        public void setMinFairValueGapPercent(double minFairValueGapPercent) {
            this.minFairValueGapPercent = minFairValueGapPercent;
        }

        public double getBosWeight() {
            return bosWeight;
        }

        public void setBosWeight(double bosWeight) {
            this.bosWeight = bosWeight;
        }

        public double getLiquiditySweepWeight() {
            return liquiditySweepWeight;
        }

        public void setLiquiditySweepWeight(double liquiditySweepWeight) {
            this.liquiditySweepWeight = liquiditySweepWeight;
        }

        public double getFairValueGapWeight() {
            return fairValueGapWeight;
        }

        public void setFairValueGapWeight(double fairValueGapWeight) {
            this.fairValueGapWeight = fairValueGapWeight;
        }
    }

    /**
     * Signal generation thresholds and filters.
     */
    public static class Signal {
        private double rsiLowerBound = 30.0;   // Don't trade when RSI < 30 (oversold)
        private double rsiUpperBound = 70.0;   // Don't trade when RSI > 70 (overbought)
        private double volumeSpikeMultiplier = 1.5; // Volume spike threshold
        private double minConfidence = 0.50;        // Minimum confidence score to generate signal
        private double disagreementPenalty = 0.10;  // Penalty when timeframes disagree

        public double getRsiLowerBound() {
            return rsiLowerBound;
        }

        public void setRsiLowerBound(double rsiLowerBound) {
            this.rsiLowerBound = rsiLowerBound;
        }

        public double getRsiUpperBound() {
            return rsiUpperBound;
        }

        public void setRsiUpperBound(double rsiUpperBound) {
            this.rsiUpperBound = rsiUpperBound;
        }

        public double getVolumeSpikeMultiplier() {
            return volumeSpikeMultiplier;
        }

        public void setVolumeSpikeMultiplier(double volumeSpikeMultiplier) {
            this.volumeSpikeMultiplier = volumeSpikeMultiplier;
        }

        public double getMinConfidence() {
            return minConfidence;
        }

        public void setMinConfidence(double minConfidence) {
            this.minConfidence = minConfidence;
        }

        public double getDisagreementPenalty() {
            return disagreementPenalty;
        }

        public void setDisagreementPenalty(double disagreementPenalty) {
            this.disagreementPenalty = disagreementPenalty;
        }
    }
}

