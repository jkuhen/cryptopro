package com.kuhen.cryptopro.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuhen.cryptopro.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XgboostRegimeClassificationModelTest {

    @Test
    void fallsBackToHeuristicWhenArtifactIsMissing() {
        AiProperties properties = new AiProperties();
        properties.getRegimeModel().setArtifactRoot("models/nonexistent_regime");
        properties.getRegimeModel().setFailOnMissingArtifact(false);

        XgboostRegimeClassificationModel model = new XgboostRegimeClassificationModel(properties, new ObjectMapper());
        RegimeDetectionResult result = model.classify(new RegimeClassificationFeatures(
                0.10,
                0.08,
                0.025,
                0.019,
                0.12,
                2.3,
                2.0,
                0.001
        ));

        assertEquals(MarketRegime.HIGH_VOLATILITY, result.regime());
    }
}

