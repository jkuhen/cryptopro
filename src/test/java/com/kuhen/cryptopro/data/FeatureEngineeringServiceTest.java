package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.data.entity.OhlcvCandleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FeatureEngineeringService feature calculation logic.
 */
class FeatureEngineeringServiceTest {

    private FeatureEngineeringService service;

    @BeforeEach
    void setUp() {
        // Create a mock service without repository dependencies
        // This tests the core calculation logic
        service = new FeatureEngineeringService(null, null);
    }

    @Test
    void testCalculateFeatures_WithCompleteCandles() {
        List<OhlcvCandleEntity> candles = createCompleteTestCandles(250);

        FeatureEngineeringService.CalculatedFeatures features = service.calculateFeatures(candles);

        assertNotNull(features);
        assertTrue(features.isValid());
        assertNotNull(features.ema20());
        assertNotNull(features.ema50());
        assertNotNull(features.ema200());
        assertNotNull(features.rsi());
        assertNotNull(features.atr());
        assertNotNull(features.volumeMa());
    }

    @Test
    void testCalculateFeatures_WithLimitedCandles() {
        // Test with only enough candles for EMA20
        List<OhlcvCandleEntity> candles = createCompleteTestCandles(50);

        FeatureEngineeringService.CalculatedFeatures features = service.calculateFeatures(candles);

        assertNotNull(features);
        // EMA20 and RSI should work with 50 candles
        assertNotNull(features.ema20());
        assertNotNull(features.rsi());
        // EMA200 requires 200 candles, should be null
        assertNull(features.ema200());
    }

    @Test
    void testCalculateFeatures_WithInsufficientCandles() {
        List<OhlcvCandleEntity> candles = createCompleteTestCandles(5);

        FeatureEngineeringService.CalculatedFeatures features = service.calculateFeatures(candles);

        // With only 5 candles, all features will be null (not enough data for any indicator)
        assertNotNull(features);
        assertNull(features.ema20());
        assertNull(features.ema50());
        assertNull(features.ema200());
        assertNull(features.rsi());
        assertNull(features.atr());
        assertNull(features.volumeMa());
        assertFalse(features.isValid());
    }

    @Test
    void testCalculateFeatures_WithNullCandles() {
        FeatureEngineeringService.CalculatedFeatures features = service.calculateFeatures(null);

        assertNull(features);
    }

    @Test
    void testCalculateFeatures_WithEmptyCandles() {
        List<OhlcvCandleEntity> candles = Arrays.asList();

        FeatureEngineeringService.CalculatedFeatures features = service.calculateFeatures(candles);

        assertNull(features);  // Empty list should return null
    }

    @Test
    void testCalculatedFeatures_IsValid() {
        FeatureEngineeringService.CalculatedFeatures validFeatures =
                new FeatureEngineeringService.CalculatedFeatures(
                        10.0, 20.0, 30.0, 50.0, 1.5, 1000.0
                );

        assertTrue(validFeatures.isValid());
    }

    @Test
    void testCalculatedFeatures_IsInvalid_WithNullValues() {
        FeatureEngineeringService.CalculatedFeatures invalidFeatures =
                new FeatureEngineeringService.CalculatedFeatures(
                        10.0, 20.0, 30.0, 50.0, 1.5, null
                );

        assertFalse(invalidFeatures.isValid());
    }

    @Test
    void testCalculatedFeatures_IsInvalid_WithOutOfRangeRsi() {
        FeatureEngineeringService.CalculatedFeatures invalidFeatures =
                new FeatureEngineeringService.CalculatedFeatures(
                        10.0, 20.0, 30.0, 150.0, 1.5, 1000.0
                );

        assertFalse(invalidFeatures.isValid());
    }

    @Test
    void testCalculatedFeatures_IsInvalid_WithInfiniteValues() {
        FeatureEngineeringService.CalculatedFeatures invalidFeatures =
                new FeatureEngineeringService.CalculatedFeatures(
                        Double.POSITIVE_INFINITY, 20.0, 30.0, 50.0, 1.5, 1000.0
                );

        assertFalse(invalidFeatures.isValid());
    }

    @Test
    void testCalculatedFeatures_ToString() {
        FeatureEngineeringService.CalculatedFeatures features =
                new FeatureEngineeringService.CalculatedFeatures(
                        10.0, 20.0, 30.0, 50.0, 1.5, 1000.0
                );

        String str = features.toString();
        assertTrue(str.contains("ema20"));
        assertTrue(str.contains("rsi"));
        assertTrue(str.contains("CalculatedFeatures"));
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Creates test candles with realistic price movements.
     */
    private List<OhlcvCandleEntity> createCompleteTestCandles(int count) {
        List<OhlcvCandleEntity> candles = new java.util.ArrayList<>();
        Instant now = Instant.now();
        double basePrice = 100.0;

        for (int i = 0; i < count; i++) {
            OhlcvCandleEntity candle = new OhlcvCandleEntity();
            candle.setSymbol("BTCUSDT");
            candle.setTimeframe("M1");
            candle.setOpenTime(now.plusSeconds(i * 60L));

            // Create a realistic price trend with some noise
            double trend = basePrice + (i * 0.1); // Slight uptrend
            double noise = Math.sin(i * 0.1) * 2; // Oscillating noise
            double volatility = 2.0; // Price volatility

            double closePrice = trend + noise;
            double openPrice = closePrice - 1.0;
            double highPrice = closePrice + volatility;
            double lowPrice = closePrice - volatility;

            candle.setOpenPrice(openPrice);
            candle.setHighPrice(highPrice);
            candle.setLowPrice(lowPrice);
            candle.setClosePrice(closePrice);
            candle.setVolume(1000.0 + (i * 10.0)); // Increasing volume

            candles.add(candle);
        }

        return candles;
    }
}



