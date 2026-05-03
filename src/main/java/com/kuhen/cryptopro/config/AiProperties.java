package com.kuhen.cryptopro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cryptopro.ai")
public class AiProperties {

    private String modelName = "xgboost-baseline";
    private final Model model = new Model();
    private final RegimeModel regimeModel = new RegimeModel();
    private final Weights weights = new Weights();
    private final Thresholds thresholds = new Thresholds();
    private final Adaptation adaptation = new Adaptation();
    private final Retraining retraining = new Retraining();

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Model getModel() {
        return model;
    }

    public Weights getWeights() {
        return weights;
    }

    public RegimeModel getRegimeModel() {
        return regimeModel;
    }

    public Thresholds getThresholds() {
        return thresholds;
    }

    public Adaptation getAdaptation() {
        return adaptation;
    }

    public Retraining getRetraining() {
        return retraining;
    }

    public static class Model {
        private String provider = "xgboost-artifact";
        private String artifactRoot = "models/signal_scorer";
        private String version = "v1";
        private String binaryFile = "model.bin";
        private String metadataFile = "metadata.json";
        private boolean failOnMissingArtifact = false;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getArtifactRoot() {
            return artifactRoot;
        }

        public void setArtifactRoot(String artifactRoot) {
            this.artifactRoot = artifactRoot;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getBinaryFile() {
            return binaryFile;
        }

        public void setBinaryFile(String binaryFile) {
            this.binaryFile = binaryFile;
        }

        public String getMetadataFile() {
            return metadataFile;
        }

        public void setMetadataFile(String metadataFile) {
            this.metadataFile = metadataFile;
        }

        public boolean isFailOnMissingArtifact() {
            return failOnMissingArtifact;
        }

        public void setFailOnMissingArtifact(boolean failOnMissingArtifact) {
            this.failOnMissingArtifact = failOnMissingArtifact;
        }
    }

    public static class RegimeModel {
        private String artifactRoot = "models/regime_classifier";
        private String version = "v1";
        private String binaryFile = "model.bin";
        private String metadataFile = "metadata.json";
        private boolean failOnMissingArtifact = false;

        public String getArtifactRoot() {
            return artifactRoot;
        }

        public void setArtifactRoot(String artifactRoot) {
            this.artifactRoot = artifactRoot;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getBinaryFile() {
            return binaryFile;
        }

        public void setBinaryFile(String binaryFile) {
            this.binaryFile = binaryFile;
        }

        public String getMetadataFile() {
            return metadataFile;
        }

        public void setMetadataFile(String metadataFile) {
            this.metadataFile = metadataFile;
        }

        public boolean isFailOnMissingArtifact() {
            return failOnMissingArtifact;
        }

        public void setFailOnMissingArtifact(boolean failOnMissingArtifact) {
            this.failOnMissingArtifact = failOnMissingArtifact;
        }
    }

    public static class Weights {
        private double bias = -0.15;
        private double trendAlignment = 1.20;
        private double rsi = 0.70;
        private double volumeSpike = 0.55;
        private double liquiditySweep = 0.60;
        private double oiChange = 0.35;
        private double fundingRate = -0.80;
        private double lowVolatilityPenalty = -0.10;
        private double highVolatilityPenalty = -0.25;

        public double getBias() {
            return bias;
        }

        public void setBias(double bias) {
            this.bias = bias;
        }

        public double getTrendAlignment() {
            return trendAlignment;
        }

        public void setTrendAlignment(double trendAlignment) {
            this.trendAlignment = trendAlignment;
        }

        public double getRsi() {
            return rsi;
        }

        public void setRsi(double rsi) {
            this.rsi = rsi;
        }

        public double getVolumeSpike() {
            return volumeSpike;
        }

        public void setVolumeSpike(double volumeSpike) {
            this.volumeSpike = volumeSpike;
        }

        public double getLiquiditySweep() {
            return liquiditySweep;
        }

        public void setLiquiditySweep(double liquiditySweep) {
            this.liquiditySweep = liquiditySweep;
        }

        public double getOiChange() {
            return oiChange;
        }

        public void setOiChange(double oiChange) {
            this.oiChange = oiChange;
        }

        public double getFundingRate() {
            return fundingRate;
        }

        public void setFundingRate(double fundingRate) {
            this.fundingRate = fundingRate;
        }

        public double getLowVolatilityPenalty() {
            return lowVolatilityPenalty;
        }

        public void setLowVolatilityPenalty(double lowVolatilityPenalty) {
            this.lowVolatilityPenalty = lowVolatilityPenalty;
        }

        public double getHighVolatilityPenalty() {
            return highVolatilityPenalty;
        }

        public void setHighVolatilityPenalty(double highVolatilityPenalty) {
            this.highVolatilityPenalty = highVolatilityPenalty;
        }
    }

    public static class Thresholds {
        private double trendingAlignmentMin = 0.45;
        private double rangingAlignmentMax = 0.18;
        private double trendingRangeCompressionMin = 0.45;
        private double manipulationVolumeSpikeMin = 2.0;
        private double manipulationOiJumpPercentMin = 1.5;
        private double manipulationSpreadBpsMin = 10.0;
        private double highVolatilityStdMin = 0.010;
        private double highVolatilityAtrPercentMin = 0.012;
        private double minProbability = 0.55;
        private double minConfidence = 0.25;

        public double getTrendingAlignmentMin() {
            return trendingAlignmentMin;
        }

        public void setTrendingAlignmentMin(double trendingAlignmentMin) {
            this.trendingAlignmentMin = trendingAlignmentMin;
        }

        public double getRangingAlignmentMax() {
            return rangingAlignmentMax;
        }

        public void setRangingAlignmentMax(double rangingAlignmentMax) {
            this.rangingAlignmentMax = rangingAlignmentMax;
        }

        public double getTrendingRangeCompressionMin() {
            return trendingRangeCompressionMin;
        }

        public void setTrendingRangeCompressionMin(double trendingRangeCompressionMin) {
            this.trendingRangeCompressionMin = trendingRangeCompressionMin;
        }

        public double getManipulationVolumeSpikeMin() {
            return manipulationVolumeSpikeMin;
        }

        public void setManipulationVolumeSpikeMin(double manipulationVolumeSpikeMin) {
            this.manipulationVolumeSpikeMin = manipulationVolumeSpikeMin;
        }

        public double getManipulationOiJumpPercentMin() {
            return manipulationOiJumpPercentMin;
        }

        public void setManipulationOiJumpPercentMin(double manipulationOiJumpPercentMin) {
            this.manipulationOiJumpPercentMin = manipulationOiJumpPercentMin;
        }

        public double getManipulationSpreadBpsMin() {
            return manipulationSpreadBpsMin;
        }

        public void setManipulationSpreadBpsMin(double manipulationSpreadBpsMin) {
            this.manipulationSpreadBpsMin = manipulationSpreadBpsMin;
        }

        public double getHighVolatilityStdMin() {
            return highVolatilityStdMin;
        }

        public void setHighVolatilityStdMin(double highVolatilityStdMin) {
            this.highVolatilityStdMin = highVolatilityStdMin;
        }

        public double getHighVolatilityAtrPercentMin() {
            return highVolatilityAtrPercentMin;
        }

        public void setHighVolatilityAtrPercentMin(double highVolatilityAtrPercentMin) {
            this.highVolatilityAtrPercentMin = highVolatilityAtrPercentMin;
        }

        public double getMinProbability() {
            return minProbability;
        }

        public void setMinProbability(double minProbability) {
            this.minProbability = minProbability;
        }

        public double getMinConfidence() {
            return minConfidence;
        }

        public void setMinConfidence(double minConfidence) {
            this.minConfidence = minConfidence;
        }
    }

    public static class Adaptation {
        private double trendingSizeMultiplier = 1.0;
        private double rangingSizeMultiplier = 0.70;
        private double manipulationSizeMultiplier = 0.30;
        private double trendingDirectionThresholdMultiplier = 0.95;
        private double rangingDirectionThresholdMultiplier = 1.15;
        private double manipulationDirectionThresholdMultiplier = 1.35;
        private boolean blockTradingInManipulation = true;

        public double getTrendingSizeMultiplier() {
            return trendingSizeMultiplier;
        }

        public void setTrendingSizeMultiplier(double trendingSizeMultiplier) {
            this.trendingSizeMultiplier = trendingSizeMultiplier;
        }

        public double getRangingSizeMultiplier() {
            return rangingSizeMultiplier;
        }

        public void setRangingSizeMultiplier(double rangingSizeMultiplier) {
            this.rangingSizeMultiplier = rangingSizeMultiplier;
        }

        public double getManipulationSizeMultiplier() {
            return manipulationSizeMultiplier;
        }

        public void setManipulationSizeMultiplier(double manipulationSizeMultiplier) {
            this.manipulationSizeMultiplier = manipulationSizeMultiplier;
        }

        public double getTrendingDirectionThresholdMultiplier() {
            return trendingDirectionThresholdMultiplier;
        }

        public void setTrendingDirectionThresholdMultiplier(double trendingDirectionThresholdMultiplier) {
            this.trendingDirectionThresholdMultiplier = trendingDirectionThresholdMultiplier;
        }

        public double getRangingDirectionThresholdMultiplier() {
            return rangingDirectionThresholdMultiplier;
        }

        public void setRangingDirectionThresholdMultiplier(double rangingDirectionThresholdMultiplier) {
            this.rangingDirectionThresholdMultiplier = rangingDirectionThresholdMultiplier;
        }

        public double getManipulationDirectionThresholdMultiplier() {
            return manipulationDirectionThresholdMultiplier;
        }

        public void setManipulationDirectionThresholdMultiplier(double manipulationDirectionThresholdMultiplier) {
            this.manipulationDirectionThresholdMultiplier = manipulationDirectionThresholdMultiplier;
        }

        public boolean isBlockTradingInManipulation() {
            return blockTradingInManipulation;
        }

        public void setBlockTradingInManipulation(boolean blockTradingInManipulation) {
            this.blockTradingInManipulation = blockTradingInManipulation;
        }
    }

    public static class Retraining {
        private boolean enabled = false;
        private long fixedDelayMs = 3_600_000;
        private int minSamples = 50;
        private int maxSamples = 1000;
        private boolean autoTrainEnabled = true;
        private String autoVersionPrefix = "auto";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }

        public int getMinSamples() {
            return minSamples;
        }

        public void setMinSamples(int minSamples) {
            this.minSamples = minSamples;
        }

        public int getMaxSamples() {
            return maxSamples;
        }

        public void setMaxSamples(int maxSamples) {
            this.maxSamples = maxSamples;
        }

        public boolean isAutoTrainEnabled() {
            return autoTrainEnabled;
        }

        public void setAutoTrainEnabled(boolean autoTrainEnabled) {
            this.autoTrainEnabled = autoTrainEnabled;
        }

        public String getAutoVersionPrefix() {
            return autoVersionPrefix;
        }

        public void setAutoVersionPrefix(String autoVersionPrefix) {
            this.autoVersionPrefix = autoVersionPrefix;
        }
    }
}
