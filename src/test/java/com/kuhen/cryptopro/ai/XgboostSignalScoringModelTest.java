package com.kuhen.cryptopro.ai;

import com.kuhen.cryptopro.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class XgboostSignalScoringModelTest {

    private final XgboostSignalScoringModel model = new XgboostSignalScoringModel(new AiProperties());

    @Test
    void higherQualityFeatureSetProducesHigherProbability() {
        SignalScoringResult strong = model.score(new SignalScoringFeatures(
                0.75,
                61.0,
                2.4,
                2.1,
                -0.002
        ));

        SignalScoringResult weak = model.score(new SignalScoringFeatures(
                -0.35,
                37.0,
                0.1,
                -1.1,
                0.015
        ));

        assertTrue(strong.probabilityOfSuccess() > weak.probabilityOfSuccess());
        assertTrue(strong.confidence() >= 0.0 && strong.confidence() <= 1.0);
    }
}

