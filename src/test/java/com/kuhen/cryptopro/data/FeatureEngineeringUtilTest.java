package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.data.entity.OhlcvCandleEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FeatureEngineeringUtil technical indicator calculations.
 */
class FeatureEngineeringUtilTest {

    // =========================================================================
    // EMA Tests
    // =========================================================================

    @Test
    void testCalculateEma_WithSufficientData() {
        List<Double> prices = Arrays.asList(
                10.0, 11.0, 12.0, 13.0, 14.0,
                15.0, 14.5, 14.0, 13.5, 13.0,
                12.5, 12.0, 11.5, 11.0, 10.5
        );

        Double ema5 = FeatureEngineeringUtil.calculateEma(prices, 5);

        assertNotNull(ema5);
        assertTrue(Double.isFinite(ema5));
        assertTrue(ema5 > 0);
    }

    @Test
    void testCalculateEma_InsufficientData() {
        List<Double> prices = Arrays.asList(10.0, 11.0);
        Double ema5 = FeatureEngineeringUtil.calculateEma(prices, 5);

        assertNull(ema5);
    }

    @Test
    void testCalculateEma_Null() {
        Double ema = FeatureEngineeringUtil.calculateEma(null, 5);
        assertNull(ema);
    }

    @Test
    void testCalculateEmaIncremental() {
        List<Double> initialPrices = Arrays.asList(
                10.0, 11.0, 12.0, 13.0, 14.0,
                15.0, 14.5, 14.0, 13.5, 13.0
        );

        Double initialEma = FeatureEngineeringUtil.calculateEma(initialPrices, 5);

        List<Double> newPrices = Arrays.asList(12.5);
        Double incrementalEma = FeatureEngineeringUtil.calculateEmaIncremental(newPrices, 5, initialEma);

        assertNotNull(incrementalEma);
        assertTrue(Double.isFinite(incrementalEma));
        // Incremental should be different from initial
        assertNotEquals(initialEma, incrementalEma);
    }

    // =========================================================================
    // RSI Tests
    // =========================================================================

    @Test
    void testCalculateRsi_WithSufficientData() {
        // Create a trend: prices going up
        List<Double> prices = Arrays.asList(
                10.0, 10.5, 11.0, 11.5, 12.0,
                12.5, 13.0, 13.5, 14.0, 14.5,
                14.0, 13.5, 13.0, 12.5, 12.0,
                12.5, 13.0, 13.5, 14.0, 14.5
        );

        Double rsi = FeatureEngineeringUtil.calculateRsi(prices, 14);

        assertNotNull(rsi);
        assertTrue(rsi >= 0.0 && rsi <= 100.0);
    }

    @Test
    void testCalculateRsi_InsufficientData() {
        List<Double> prices = Arrays.asList(10.0, 11.0, 12.0);
        Double rsi = FeatureEngineeringUtil.calculateRsi(prices, 14);

        assertNull(rsi);
    }

    @Test
    void testCalculateRsi_Null() {
        Double rsi = FeatureEngineeringUtil.calculateRsi(null, 14);
        assertNull(rsi);
    }

    // =========================================================================
    // ATR Tests
    // =========================================================================

    @Test
    void testCalculateAtrFromPrices_WithSufficientData() {
        List<Double> highs = Arrays.asList(
                11.0, 12.0, 13.0, 14.0, 15.0,
                16.0, 15.5, 15.0, 14.5, 14.0,
                13.5, 13.0, 12.5, 12.0, 11.5
        );
        List<Double> lows = Arrays.asList(
                9.0, 10.0, 11.0, 12.0, 13.0,
                14.0, 13.5, 13.0, 12.5, 12.0,
                11.5, 11.0, 10.5, 10.0, 9.5
        );
        List<Double> closes = Arrays.asList(
                10.0, 11.0, 12.0, 13.0, 14.0,
                15.0, 14.5, 14.0, 13.5, 13.0,
                12.5, 12.0, 11.5, 11.0, 10.5
        );

        Double atr = FeatureEngineeringUtil.calculateAtrFromPrices(highs, lows, closes, 14);

        assertNotNull(atr);
        assertTrue(Double.isFinite(atr));
        assertTrue(atr >= 0);
    }

    @Test
    void testCalculateAtrFromPrices_InsufficientData() {
        List<Double> highs = Arrays.asList(11.0, 12.0);
        List<Double> lows = Arrays.asList(9.0, 10.0);
        List<Double> closes = Arrays.asList(10.0, 11.0);

        Double atr = FeatureEngineeringUtil.calculateAtrFromPrices(highs, lows, closes, 14);

        assertNull(atr);
    }

    @Test
    void testCalculateAtrFromPrices_NullInputs() {
        List<Double> lows = Arrays.asList(9.0, 10.0);
        List<Double> closes = Arrays.asList(10.0, 11.0);

        Double atr = FeatureEngineeringUtil.calculateAtrFromPrices(null, lows, closes, 14);
        assertNull(atr);

        List<Double> highs = Arrays.asList(11.0, 12.0);
        atr = FeatureEngineeringUtil.calculateAtrFromPrices(highs, null, closes, 14);
        assertNull(atr);

        atr = FeatureEngineeringUtil.calculateAtrFromPrices(highs, lows, null, 14);
        assertNull(atr);
    }

    // =========================================================================
    // Volume MA Tests
    // =========================================================================

    @Test
    void testCalculateVolumeMA_WithSufficientData() {
        List<Double> volumes = Arrays.asList(
                1000.0, 1100.0, 1200.0, 1150.0, 1250.0,
                1300.0, 1350.0, 1400.0, 1450.0, 1500.0,
                1550.0, 1600.0, 1650.0, 1700.0, 1750.0,
                1800.0, 1850.0, 1900.0, 1950.0, 2000.0
        );

        Double volumeMa = FeatureEngineeringUtil.calculateVolumeMA(volumes, 5);

        assertNotNull(volumeMa);
        assertTrue(Double.isFinite(volumeMa));
        assertTrue(volumeMa > 0);
        // For last 5 volumes [1850, 1900, 1950, 2000], avg should be > 1500
        assertTrue(volumeMa >= 1500);
    }

    @Test
    void testCalculateVolumeMA_InsufficientData() {
        List<Double> volumes = Arrays.asList(1000.0, 1100.0);
        Double volumeMa = FeatureEngineeringUtil.calculateVolumeMA(volumes, 5);

        assertNull(volumeMa);
    }

    @Test
    void testCalculateVolumeMA_Null() {
        Double volumeMa = FeatureEngineeringUtil.calculateVolumeMA(null, 5);
        assertNull(volumeMa);
    }

    @Test
    void testCalculateVolumeMaFromCandles() {
        List<OhlcvCandleEntity> candles = createTestCandles(10);

        Double volumeMa = FeatureEngineeringUtil.calculateVolumeMaFromCandles(candles, 5);

        assertNotNull(volumeMa);
        assertTrue(Double.isFinite(volumeMa));
        assertTrue(volumeMa > 0);
    }

    // =========================================================================
    // Validation Tests
    // =========================================================================

    @Test
    void testIsValidFeatureValue_ValidValues() {
        assertTrue(FeatureEngineeringUtil.isValidFeatureValue(50.0, 0.0, 100.0));
        assertTrue(FeatureEngineeringUtil.isValidFeatureValue(0.0, 0.0, 100.0));
        assertTrue(FeatureEngineeringUtil.isValidFeatureValue(100.0, 0.0, 100.0));
        assertTrue(FeatureEngineeringUtil.isValidFeatureValue(50.5, 0.0, null));
    }

    @Test
    void testIsValidFeatureValue_InvalidValues() {
        assertFalse(FeatureEngineeringUtil.isValidFeatureValue(-1.0, 0.0, 100.0));
        assertFalse(FeatureEngineeringUtil.isValidFeatureValue(101.0, 0.0, 100.0));
        assertFalse(FeatureEngineeringUtil.isValidFeatureValue(null, 0.0, 100.0));
        assertFalse(FeatureEngineeringUtil.isValidFeatureValue(Double.NaN, 0.0, 100.0));
        assertFalse(FeatureEngineeringUtil.isValidFeatureValue(Double.POSITIVE_INFINITY, 0.0, 100.0));
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private List<OhlcvCandleEntity> createTestCandles(int count) {
        List<OhlcvCandleEntity> candles = new java.util.ArrayList<>();
        Instant now = Instant.now();

        for (int i = 0; i < count; i++) {
            OhlcvCandleEntity candle = new OhlcvCandleEntity();
            candle.setSymbol("TESTUSDT");
            candle.setTimeframe("M1");
            candle.setOpenTime(now.plusSeconds(i * 60L));
            candle.setOpenPrice(10.0 + i);
            candle.setHighPrice(11.0 + i);
            candle.setLowPrice(9.0 + i);
            candle.setClosePrice(10.5 + i);
            candle.setVolume(1000.0 + (i * 100));
            candles.add(candle);
        }

        return candles;
    }
}

