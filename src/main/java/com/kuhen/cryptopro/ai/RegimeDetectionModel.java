package com.kuhen.cryptopro.ai;

import org.springframework.stereotype.Service;

@Service
public class RegimeDetectionModel {

    private final XgboostRegimeClassificationModel regimeClassificationModel;

    public RegimeDetectionModel(XgboostRegimeClassificationModel regimeClassificationModel) {
        this.regimeClassificationModel = regimeClassificationModel;
    }

    public RegimeDetectionResult detect(RegimeClassificationFeatures features) {
        return regimeClassificationModel.classify(features);
    }

    public RegimeDetectionResult detect(SignalScoringFeatures features, double spreadBps) {
        // Backward-compatible adapter when only score features are available.
        RegimeClassificationFeatures mapped = new RegimeClassificationFeatures(
                features.trendScore(),
                features.trendScore() * 0.8,
                Math.max(0.0, spreadBps / 10_000.0),
                Math.max(0.0, spreadBps / 8_000.0),
                1.0 - Math.min(1.0, Math.abs(features.trendScore())),
                features.volumeSpike(),
                features.openInterestChange(),
                features.fundingRate()
        );
        return detect(mapped);
    }
}

