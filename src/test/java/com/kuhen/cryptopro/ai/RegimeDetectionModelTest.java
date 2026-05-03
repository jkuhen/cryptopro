package com.kuhen.cryptopro.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuhen.cryptopro.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegimeDetectionModelTest {

    private final RegimeDetectionModel model = new RegimeDetectionModel(
            new XgboostRegimeClassificationModel(new AiProperties(), new ObjectMapper())
    );

    @Test
    void detectsHighVolatilityFromHistoricalFeatures() {
        RegimeDetectionResult result = model.detect(new RegimeClassificationFeatures(
                0.08,
                0.05,
                0.022,
                0.018,
                0.10,
                2.4,
                2.1,
                0.001
        ));

        assertEquals(MarketRegime.HIGH_VOLATILITY, result.regime());
    }

    @Test
    void detectsRangingWhenTrendAndVolatilityAreLow() {
        RegimeDetectionResult result = model.detect(new RegimeClassificationFeatures(
                0.02,
                0.01,
                0.003,
                0.004,
                0.35,
                0.4,
                0.2,
                0.0003
        ));

        assertEquals(MarketRegime.RANGING, result.regime());
    }
}

