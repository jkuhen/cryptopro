package com.kuhen.cryptopro.ai;

import com.kuhen.cryptopro.config.AiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "cryptopro.ai.model", name = "provider", havingValue = "baseline")
public class XgboostSignalScoringModel implements SignalScoringModel, ModelInfoProvider {

    private final AiProperties aiProperties;

    public XgboostSignalScoringModel(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Override
    public SignalScoringResult score(SignalScoringFeatures features) {
        AiProperties.Weights weights = aiProperties.getWeights();

        double trend = clamp(features.trendScore(), -1.0, 1.0);
        double rsi = normalizeRsi(features.rsi());
        double volumeSpike = clamp(features.volumeSpike() / 3.0, -1.0, 1.0);
        double oiChange = clamp(features.openInterestChange() / 4.0, -1.0, 1.0);
        double funding = clamp(features.fundingRate() * 25.0, -1.0, 1.0);

        double logit = weights.getBias()
                + (weights.getTrendAlignment() * trend)
                + (weights.getRsi() * rsi)
                + (weights.getVolumeSpike() * volumeSpike)
                + (weights.getOiChange() * oiChange)
                + (weights.getFundingRate() * funding);

        double probability = sigmoid(logit);
        double confidence = Math.min(1.0, Math.abs(probability - 0.5) * 2.0);

        String notes = "xgboost-baseline features => trend=" + round(trend)
                + ", rsi=" + round(features.rsi())
                + ", volSpike=" + round(volumeSpike)
                + ", oi%=" + round(features.openInterestChange())
                + ", funding=" + round(features.fundingRate());

        return new SignalScoringResult(probability, confidence, aiProperties.getModelName(), "baseline", notes);
    }

    @Override
    public ModelInfoResponse currentModelInfo() {
        return new ModelInfoResponse(
                "baseline",
                aiProperties.getModelName(),
                "baseline",
                "N/A",
                true,
                java.util.Map.of(),
                "Baseline scoring mode is active"
        );
    }

    private double sigmoid(double value) {
        return 1.0 / (1.0 + Math.exp(-value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double normalizeRsi(double rsi) {
        return clamp((rsi - 50.0) / 50.0, -1.0, 1.0);
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}



