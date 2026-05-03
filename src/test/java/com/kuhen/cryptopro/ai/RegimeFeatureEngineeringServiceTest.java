package com.kuhen.cryptopro.ai;

import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.Timeframe;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RegimeFeatureEngineeringServiceTest {

    private final RegimeFeatureEngineeringService service = new RegimeFeatureEngineeringService();

    @Test
    void buildsHistoricalRegimeFeaturesFromCandles() {
        List<Candle> h1 = trendCandles(Timeframe.H1, 40, 100.0, 0.5, 1.2);
        List<Candle> m15 = trendCandles(Timeframe.M15, 60, 100.0, 0.2, 0.8);
        List<Candle> m1 = trendCandles(Timeframe.M1, 120, 100.0, 0.05, 0.5);

        RegimeClassificationFeatures features = service.build(h1, m15, m1, 1.8, 1.2, -0.001);

        assertTrue(features.h1TrendSlope() > 0.0);
        assertTrue(features.m15TrendSlope() > 0.0);
        assertTrue(features.realizedVolatility() >= 0.0);
        assertTrue(features.atrPercent() >= 0.0);
        assertTrue(features.rangeCompression() >= 0.0 && features.rangeCompression() <= 1.0);
    }

    private List<Candle> trendCandles(Timeframe timeframe, int count, double start, double slope, double amplitude) {
        List<Candle> candles = new ArrayList<>();
        Instant base = Instant.parse("2026-04-25T00:00:00Z");
        for (int i = 0; i < count; i++) {
            double open = start + (i * slope);
            double close = open + slope * 0.6;
            double high = Math.max(open, close) + amplitude;
            double low = Math.min(open, close) - amplitude;
            candles.add(new Candle("BTCUSDT", timeframe, base.plusSeconds(i * 60L), open, high, low, close, 1000 + i));
        }
        return candles;
    }
}

