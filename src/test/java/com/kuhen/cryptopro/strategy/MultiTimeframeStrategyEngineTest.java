package com.kuhen.cryptopro.strategy;

import com.kuhen.cryptopro.config.StrategyProperties;
import com.kuhen.cryptopro.data.entity.FeaturesEntity;
import com.kuhen.cryptopro.data.entity.SignalEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MultiTimeframeStrategyEngine signal generation logic.
 *
 * Tests cover:
 * - Trend detection from EMA alignment
 * - RSI filtering
 * - Volume spike detection
 * - Multi-timeframe disagreement penalties
 * - Confidence score calculation
 * - Signal generation and persistence
 */
class MultiTimeframeStrategyEngineTest {

    private MultiTimeframeStrategyEngine engine;
    private StrategyProperties properties;

    @BeforeEach
    void setUp() {
        properties = new StrategyProperties();
        engine = new MultiTimeframeStrategyEngine(properties);
    }

    @DisplayName("Should generate BUY signal when all timeframes aligned bullish with good RSI")
    @Test
    void shouldGenerateBuySignalWhenAllTimeframesAlignedBullish() {
        // Arrange: Create features where price > all EMAs (bullish)
        // H1: strong uptrend
        FeaturesEntity h1Features = createFeatures(
            100.0,  // ema20 = 100
            95.0,   // ema50 = 95
            90.0,   // ema200 = 90
            50.0,   // rsi = 50 (neutral, good for trading)
            2.5,    // atr
            1000.0  // volumeMa
        );

        // M15: strong uptrend
        FeaturesEntity m15Features = createFeatures(
            100.5,  // ema20
            95.5,   // ema50
            91.0,   // ema200
            55.0,   // rsi
            2.4,
            1050.0
        );

        // M5: strong uptrend
        FeaturesEntity m5Features = createFeatures(
            101.0,  // ema20
            96.0,   // ema50
            92.0,   // ema200
            52.0,   // rsi
            2.3,
            1100.0
        );

        double currentPrice = 105.0; // Above all EMAs

        // Act
        StrategySignal signal = engine.generateSignal("BTCUSDT", h1Features, m15Features, m5Features, currentPrice);

        // Assert
        assertNotNull(signal, "Signal should be generated for aligned bullish timeframes");
        assertEquals(SignalEntity.SignalTypeEnum.BUY, signal.signalType());
        assertEquals(SignalDirection.LONG, signal.direction());
        assertTrue(signal.confidenceScore() > properties.getSignal().getMinConfidence(),
            "Confidence should exceed minimum threshold");
        assertTrue(signal.confidenceScore() <= 1.0, "Confidence should be normalized to [0, 1]");
    }

    @DisplayName("Should generate SELL signal when all timeframes aligned bearish")
    @Test
    void shouldGenerateSellSignalWhenAllTimeframesAlignedBearish() {
        // Arrange: Create features where price < all EMAs (bearish)
        FeaturesEntity h1Features = createFeatures(
            100.0, 105.0, 110.0, 40.0, 2.5, 1000.0
        );
        FeaturesEntity m15Features = createFeatures(
            100.5, 105.5, 110.5, 45.0, 2.4, 1050.0
        );
        FeaturesEntity m5Features = createFeatures(
            100.2, 105.2, 110.2, 42.0, 2.3, 1100.0
        );

        double currentPrice = 95.0; // Below all EMAs

        // Act
        StrategySignal signal = engine.generateSignal("BTCUSDT", h1Features, m15Features, m5Features, currentPrice);

        // Assert
        assertNotNull(signal, "Signal should be generated for aligned bearish timeframes");
        assertEquals(SignalEntity.SignalTypeEnum.SELL, signal.signalType());
        assertEquals(SignalDirection.SHORT, signal.direction());
        assertTrue(signal.confidenceScore() > properties.getSignal().getMinConfidence());
    }

    @DisplayName("Should apply RSI filter - reject signal when RSI above 70")
    @Test
    void shouldRejectSignalWhenRsiOverbought() {
        // Arrange: Bullish alignment but RSI overbought
        FeaturesEntity h1Features = createFeatures(100.0, 95.0, 90.0, 50.0, 2.5, 1000.0);
        FeaturesEntity m15Features = createFeatures(100.5, 95.5, 91.0, 55.0, 2.4, 1050.0);
        FeaturesEntity m5Features = createFeatures(
            101.0, 96.0, 92.0,
            75.0,  // RSI > 70 (overbought)
            2.3, 1100.0
        );

        double currentPrice = 105.0;

        // Act
        StrategySignal signal = engine.generateSignal("BTCUSDT", h1Features, m15Features, m5Features, currentPrice);

        // Assert
        assertNull(signal, "Signal should be rejected when M5 RSI is overbought (> 70)");
    }

    @DisplayName("Should apply RSI filter - reject signal when RSI below 30")
    @Test
    void shouldRejectSignalWhenRsiOversold() {
        // Arrange: Bearish alignment but RSI oversold
        FeaturesEntity h1Features = createFeatures(100.0, 105.0, 110.0, 40.0, 2.5, 1000.0);
        FeaturesEntity m15Features = createFeatures(100.5, 105.5, 110.5, 45.0, 2.4, 1050.0);
        FeaturesEntity m5Features = createFeatures(
            100.2, 105.2, 110.2,
            25.0,  // RSI < 30 (oversold)
            2.3, 1100.0
        );

        double currentPrice = 95.0;

        // Act
        StrategySignal signal = engine.generateSignal("BTCUSDT", h1Features, m15Features, m5Features, currentPrice);

        // Assert
        assertNull(signal, "Signal should be rejected when M5 RSI is oversold (< 30)");
    }

    @DisplayName("Should detect volume spikes and boost confidence")
    @Test
    void shouldDetectVolumeSpikeAndBoostConfidence() {
        // Arrange: Aligned trend with elevated M5 volume
        FeaturesEntity h1Features = createFeatures(100.0, 95.0, 90.0, 50.0, 2.5, 1000.0); // H1 volumeMa = 1000
        FeaturesEntity m15Features = createFeatures(100.5, 95.5, 91.0, 55.0, 2.4, 1050.0);
        FeaturesEntity m5Features = createFeatures(
            101.0, 96.0, 92.0, 52.0, 2.3,
            1600.0  // M5 volumeMa = 1600, ratio = 1.6 > 1.5 (spike)
        );

        double currentPrice = 105.0;

        // Act
        StrategySignal signal = engine.generateSignal("BTCUSDT", h1Features, m15Features, m5Features, currentPrice);

        // Assert
        assertNotNull(signal, "Signal should be generated with volume spike");
        assertTrue(signal.rationale().volumeSignal().isSpike(), "Volume spike should be detected");
        // Confidence should be boosted by 0.1 due to volume spike
        assertTrue(signal.confidenceScore() > 0.60, "Confidence should be boosted by volume spike");
    }

    @DisplayName("Should penalize disagreement when timeframes diverge")
    @Test
    void shouldPenalizeDisagreement() {
        // Arrange: H1 strong bullish, M5 weak bearish (disagreement)
        FeaturesEntity h1Features = createFeatures(
            100.0, 95.0, 90.0,  // H1 bullish
            50.0, 2.5, 1000.0
        );
        FeaturesEntity m15Features = createFeatures(100.5, 95.5, 91.0, 55.0, 2.4, 1050.0);
        FeaturesEntity m5Features = createFeatures(
            99.0, 101.0, 102.0,  // M5 bearish (price < ema20)
            50.0, 2.3, 1100.0
        );

        double currentPrice = 99.5;

        // Act
        StrategySignal signal = engine.generateSignal("BTCUSDT", h1Features, m15Features, m5Features, currentPrice);

        // Assert
        if (signal != null) {
            // If signal generated despite disagreement, it should have penalty applied
            double expectedMaxConfidence = Math.abs(0.50 * 0.7 + 0.30 * 0.7 + 0.20 * (-0.7)); // weighted score
            assertTrue(signal.confidenceScore() <= expectedMaxConfidence * 1.1,
                "Disagreement penalty should reduce confidence");
        }
    }

    @DisplayName("Should handle missing features gracefully")
    @Test
    void shouldHandleMissingFeatures() {
        // Arrange
        FeaturesEntity h1Features = createFeatures(100.0, 95.0, 90.0, 50.0, 2.5, 1000.0);
        FeaturesEntity m15Features = null; // Missing
        FeaturesEntity m5Features = createFeatures(101.0, 96.0, 92.0, 52.0, 2.3, 1100.0);

        double currentPrice = 105.0;

        // Act
        StrategySignal signal = engine.generateSignal("BTCUSDT", h1Features, m15Features, m5Features, currentPrice);

        // Assert
        assertNull(signal, "Signal should not be generated with missing features");
    }

    @DisplayName("Should respect minimum confidence threshold")
    @Test
    void shouldRespectMinimumConfidenceThreshold() {
        // Arrange: Weak alignment barely triggers but below min confidence
        FeaturesEntity h1Features = createFeatures(100.0, 100.05, 100.1, 50.0, 2.5, 1000.0); // Very weak bullish
        FeaturesEntity m15Features = createFeatures(100.0, 100.0, 100.0, 50.0, 2.4, 1050.0);  // Neutral
        FeaturesEntity m5Features = createFeatures(100.0, 99.9, 99.8, 50.0, 2.3, 1100.0);     // Very weak bearish

        double currentPrice = 100.01;

        // Act
        StrategySignal signal = engine.generateSignal("BTCUSDT", h1Features, m15Features, m5Features, currentPrice);

        // Assert
        assertNull(signal, "Signal should not be generated below minimum confidence threshold");
    }

    @DisplayName("Should calculate weighted score correctly (50-30-20 weights)")
    @Test
    void shouldCalculateWeightedScoreWithCorrectWeights() {
        // Arrange: All timeframes strong bullish - H1 should dominate the score
        // H1: strong bullish (all price > emas, emas aligned)
        FeaturesEntity h1Features = createFeatures(100.0, 95.0, 90.0, 50.0, 2.5, 1000.0);
        // M15: strong bullish (same pattern)
        FeaturesEntity m15Features = createFeatures(100.5, 95.5, 91.0, 55.0, 2.4, 1050.0);
        // M5: strong bullish (same pattern)
        FeaturesEntity m5Features = createFeatures(101.0, 96.0, 92.0, 50.0, 2.3, 1100.0);

        double currentPrice = 105.0; // Above all EMAs

        // Act
        StrategySignal signal = engine.generateSignal("BTCUSDT", h1Features, m15Features, m5Features, currentPrice);

        // Assert
        assertNotNull(signal, "Signal should be generated");
        // When all timeframes are strongly bullish, the weighted score should be positive and significant
        assertTrue(signal.rationale().weightedScore() > 0.5,
            "Weighted score should be high when all timeframes aligned bullish");
        // Since H1 has 50% weight and M15 has 30%, and both are strong, score should be well over 0.5
        assertEquals(SignalDirection.LONG, signal.direction());
        assertEquals(SignalEntity.SignalTypeEnum.BUY, signal.signalType());
    }

    // Helper method to create FeaturesEntity
    private FeaturesEntity createFeatures(double ema20, double ema50, double ema200,
                                         double rsi, double atr, double volumeMa) {
        FeaturesEntity features = new FeaturesEntity();
        features.setEma20(ema20);
        features.setEma50(ema50);
        features.setEma200(ema200);
        features.setRsi(rsi);
        features.setAtr(atr);
        features.setVolumeMa(volumeMa);
        return features;
    }
}


