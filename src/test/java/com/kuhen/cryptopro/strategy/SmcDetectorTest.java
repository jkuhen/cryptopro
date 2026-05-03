package com.kuhen.cryptopro.strategy;

import com.kuhen.cryptopro.config.StrategyProperties;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.Timeframe;
import com.kuhen.cryptopro.preprocess.PreprocessedCandle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmcDetectorTest {

    private final StrategyProperties properties = new StrategyProperties();
    private final SmcDetector detector = new SmcDetector(new PriceActionPatternDetector(properties), properties);

    @Test
    void detectsBullishBreakOfStructureFromPriceAction() {
        SmcSignal signal = detector.detect(List.of(
                candle(100, 103, 99, 101),
                candle(101, 104, 100, 103),
                candle(103, 106, 102, 105),
                candle(105, 104, 101, 102),
                candle(102, 103, 100, 101),
                candle(101, 108, 100, 107)
        ));

        assertTrue(signal.breakOfStructure());
        assertEquals(SignalDirection.LONG, signal.breakOfStructureSignal().direction());
        assertTrue(signal.breakOfStructureSignal().strength() > 0.0);
        assertEquals(SignalDirection.LONG, signal.direction());
    }

    @Test
    void detectsBearishLiquiditySweepFromPriceAction() {
        SmcSignal signal = detector.detect(List.of(
                candle(100, 103, 99, 101),
                candle(101, 104, 100, 103),
                candle(103, 106, 102, 105),
                candle(105, 104, 101, 102),
                candle(102, 103, 100, 101),
                candle(101, 108, 99, 104)
        ));

        assertTrue(signal.liquiditySweep());
        assertEquals(SignalDirection.SHORT, signal.liquiditySweepSignal().direction());
        assertTrue(signal.liquiditySweepSignal().strength() > 0.0);
        assertFalse(signal.breakOfStructure());
    }

    @Test
    void detectsBullishFairValueGapFromThreeCandleImbalance() {
        SmcSignal signal = detector.detect(List.of(
                candle(100, 102, 99, 101),
                candle(101, 103, 100, 102),
                candle(102, 104, 101, 103),
                candle(103, 110, 103, 109),
                candle(109, 109.5, 105, 108)
        ));

        assertTrue(signal.fairValueGap());
        assertEquals(SignalDirection.LONG, signal.fairValueGapSignal().direction());
        assertTrue(signal.fairValueGapSignal().strength() > 0.0);
    }

    private PreprocessedCandle candle(double open, double high, double low, double close) {
        Candle candle = new Candle("BTCUSDT", Timeframe.M5, Instant.now(), open, high, low, close, 1_000.0);
        return new PreprocessedCandle(candle, close, 0.0, 1.0);
    }
}

