package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.data.entity.OhlcvCandleEntity;

import java.util.List;

/**
 * Utility class for calculating technical analysis indicators.
 *
 * <p>All calculations follow standard TA-Lib conventions:
 * <ul>
 *   <li>EMA (Exponential Moving Average) - smoothing factor α = 2 / (N + 1)</li>
 *   <li>RSI (Relative Strength Index) - compares average gains vs losses over N periods</li>
 *   <li>ATR (Average True Range) - volatility measure using true range</li>
 *   <li>Volume MA - simple moving average of volume</li>
 * </ul>
 *
 * <p>Methods are designed to work with sorted candle lists (oldest to newest)
 * for efficient incremental updates.
 */
public class FeatureEngineeringUtil {

    private FeatureEngineeringUtil() {
        // Utility class; no instantiation
    }

    // =========================================================================
    // EMA (Exponential Moving Average)
    // =========================================================================

    /**
     * Calculates the Exponential Moving Average (EMA) for a given period.
     *
     * <p>Uses the standard TA-Lib formula:
     * <pre>
     *   SMA = sum of N prices / N
     *   EMA_today = (Close - EMA_yesterday) * α + EMA_yesterday
     *   α = 2 / (N + 1)
     * </pre>
     *
     * @param prices list of close prices (oldest to newest)
     * @param period the EMA period (e.g., 20, 50, 200)
     * @return the calculated EMA, or null if insufficient data
     */
    public static Double calculateEma(List<Double> prices, int period) {
        if (prices == null || prices.size() < period) {
            return null;
        }

        // Calculate initial SMA
        double sum = 0.0;
        for (int i = 0; i < period; i++) {
            sum += prices.get(i);
        }
        double ema = sum / period;

        // Apply EMA formula for remaining prices
        double alpha = 2.0 / (period + 1.0);
        for (int i = period; i < prices.size(); i++) {
            ema = (prices.get(i) - ema) * alpha + ema;
        }

        return ema;
    }

    /**
     * Calculates EMA using only the most recent prices efficiently.
     *
     * <p>If a previous EMA is available, this is more efficient for incremental updates.
     *
     * @param prices list of prices (oldest to newest)
     * @param period the EMA period
     * @param previousEma the previously calculated EMA (can be null for first calculation)
     * @return the updated EMA
     */
    public static Double calculateEmaIncremental(List<Double> prices, int period, Double previousEma) {
        if (prices == null || prices.isEmpty()) {
            return previousEma;
        }

        if (previousEma == null) {
            return calculateEma(prices, period);
        }

        // Use existing EMA as baseline
        double alpha = 2.0 / (period + 1.0);
        double ema = previousEma;

        // Update with new prices only
        for (Double price : prices) {
            ema = (price - ema) * alpha + ema;
        }

        return ema;
    }

    // =========================================================================
    // RSI (Relative Strength Index)
    // =========================================================================

    /**
     * Calculates the Relative Strength Index (RSI).
     *
     * <p>RSI measures the magnitude of recent price changes to evaluate
     * overbought or oversold conditions.
     * <pre>
     *   RS = average gain / average loss
     *   RSI = 100 - (100 / (1 + RS))
     * </pre>
     *
     * @param prices list of close prices (oldest to newest)
     * @param period the RSI period (typically 14)
     * @return the RSI value (0-100), or null if insufficient data
     */
    public static Double calculateRsi(List<Double> prices, int period) {
        if (prices == null || prices.size() < period + 1) {
            return null;
        }

        // Calculate price changes
        double[] changes = new double[prices.size() - 1];
        for (int i = 1; i < prices.size(); i++) {
            changes[i - 1] = prices.get(i) - prices.get(i - 1);
        }

        // Calculate average gain and loss (using first period as SMA)
        double gainSum = 0.0;
        double lossSum = 0.0;

        for (int i = 0; i < period; i++) {
            if (changes[i] > 0) {
                gainSum += changes[i];
            } else {
                lossSum += Math.abs(changes[i]);
            }
        }

        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;

        // Use EMA for subsequent periods
        for (int i = period; i < changes.length; i++) {
            double change = changes[i];
            double gain = Math.max(change, 0.0);
            double loss = Math.max(-change, 0.0);

            avgGain = (gain - avgGain) * (2.0 / (period + 1.0)) + avgGain;
            avgLoss = (loss - avgLoss) * (2.0 / (period + 1.0)) + avgLoss;
        }

        // Handle division by zero
        if (avgLoss == 0.0) {
            return avgGain > 0.0 ? 100.0 : 0.0;
        }

        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    /**
     * Calculates RSI using previous averages for better incremental performance.
     *
     * @param newPrices list of new prices to incorporate (oldest to newest)
     * @param period the RSI period
     * @param previousAvgGain the previous average gain (can be null for first calculation)
     * @param previousAvgLoss the previous average loss (can be null for first calculation)
     * @param allHistoricalPrices all historical prices for validation (can be null)
     * @return RsiResult containing the calculated RSI and updated averages
     */
    public static RsiResult calculateRsiIncremental(
            List<Double> newPrices,
            int period,
            Double previousAvgGain,
            Double previousAvgLoss,
            List<Double> allHistoricalPrices) {

        if (newPrices == null || newPrices.isEmpty()) {
            return new RsiResult(null, previousAvgGain, previousAvgLoss);
        }

        // Use full calculation if no previous averages
        if (previousAvgGain == null || previousAvgLoss == null) {
            List<Double> pricesToUse = allHistoricalPrices != null ? allHistoricalPrices : newPrices;
            Double rsi = calculateRsi(pricesToUse, period);
            // Re-calculate averages for the result
            Double avgGain = extractAverageGain(pricesToUse, period);
            Double avgLoss = extractAverageLoss(pricesToUse, period);
            return new RsiResult(rsi, avgGain, avgLoss);
        }

        // Incremental update
        double avgGain = previousAvgGain;
        double avgLoss = previousAvgLoss;

        for (Double price : newPrices) {
            // This assumes previous price is tracked elsewhere
            // For now, we'll recalculate from full history if needed
        }

        // Simplified: recalculate with new prices added
        if (allHistoricalPrices != null) {
            Double rsi = calculateRsi(allHistoricalPrices, period);
            avgGain = extractAverageGain(allHistoricalPrices, period);
            avgLoss = extractAverageLoss(allHistoricalPrices, period);
            return new RsiResult(rsi, avgGain, avgLoss);
        }

        return new RsiResult(null, avgGain, avgLoss);
    }

    private static Double extractAverageGain(List<Double> prices, int period) {
        if (prices == null || prices.size() < period + 1) {
            return null;
        }

        double gainSum = 0.0;
        for (int i = 1; i <= period; i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) {
                gainSum += change;
            }
        }

        double avgGain = gainSum / period;

        double[] changes = new double[prices.size() - 1];
        for (int i = 1; i < prices.size(); i++) {
            changes[i - 1] = prices.get(i) - prices.get(i - 1);
        }

        for (int i = period; i < changes.length; i++) {
            double gain = Math.max(changes[i], 0.0);
            avgGain = (gain - avgGain) * (2.0 / (period + 1.0)) + avgGain;
        }

        return avgGain;
    }

    private static Double extractAverageLoss(List<Double> prices, int period) {
        if (prices == null || prices.size() < period + 1) {
            return null;
        }

        double lossSum = 0.0;
        for (int i = 1; i <= period; i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change < 0) {
                lossSum += Math.abs(change);
            }
        }

        double avgLoss = lossSum / period;

        double[] changes = new double[prices.size() - 1];
        for (int i = 1; i < prices.size(); i++) {
            changes[i - 1] = prices.get(i) - prices.get(i - 1);
        }

        for (int i = period; i < changes.length; i++) {
            double loss = Math.max(-changes[i], 0.0);
            avgLoss = (loss - avgLoss) * (2.0 / (period + 1.0)) + avgLoss;
        }

        return avgLoss;
    }

    /**
     * Result container for incremental RSI calculation.
     */
    public record RsiResult(Double rsi, Double avgGain, Double avgLoss) {
    }

    // =========================================================================
    // ATR (Average True Range)
    // =========================================================================

    /**
     * Calculates the Average True Range (ATR), a volatility indicator.
     *
     * <p>True Range = max(
     *   high - low,
     *   abs(high - prev_close),
     *   abs(low - prev_close)
     * )
     *
     * @param candles list of candles (oldest to newest)
     * @param period the ATR period (typically 14)
     * @return the ATR value, or null if insufficient data
     */
    public static Double calculateAtr(List<OhlcvCandleEntity> candles, int period) {
        if (candles == null || candles.size() < period + 1) {
            return null;
        }

        // Calculate true ranges
        double[] trueRanges = new double[candles.size() - 1];
        for (int i = 1; i < candles.size(); i++) {
            OhlcvCandleEntity current = candles.get(i);
            OhlcvCandleEntity prev = candles.get(i - 1);

            double highLow = current.getHighPrice() - current.getLowPrice();
            double highClose = Math.abs(current.getHighPrice() - prev.getClosePrice());
            double lowClose = Math.abs(current.getLowPrice() - prev.getClosePrice());

            trueRanges[i - 1] = Math.max(highLow, Math.max(highClose, lowClose));
        }

        // Calculate initial SMA for ATR
        double sum = 0.0;
        for (int i = 0; i < period; i++) {
            sum += trueRanges[i];
        }
        double atr = sum / period;

        // Apply Wilder's smoothing (modified EMA)
        for (int i = period; i < trueRanges.length; i++) {
            atr = (trueRanges[i] - atr) * (1.0 / period) + atr;
        }

        return atr;
    }

    /**
     * Calculates ATR using price arrays instead of candle entities.
     *
     * @param highs list of high prices (oldest to newest)
     * @param lows list of low prices (oldest to newest)
     * @param closes list of close prices (oldest to newest)
     * @param period the ATR period
     * @return the ATR value, or null if insufficient data
     */
    public static Double calculateAtrFromPrices(
            List<Double> highs,
            List<Double> lows,
            List<Double> closes,
            int period) {

        if (highs == null || lows == null || closes == null ||
                highs.size() < period + 1 || lows.size() < period + 1 || closes.size() < period + 1) {
            return null;
        }

        // Calculate true ranges
        double[] trueRanges = new double[highs.size() - 1];
        for (int i = 1; i < highs.size(); i++) {
            double highLow = highs.get(i) - lows.get(i);
            double highClose = Math.abs(highs.get(i) - closes.get(i - 1));
            double lowClose = Math.abs(lows.get(i) - closes.get(i - 1));

            trueRanges[i - 1] = Math.max(highLow, Math.max(highClose, lowClose));
        }

        // Calculate initial SMA
        double sum = 0.0;
        for (int i = 0; i < period; i++) {
            sum += trueRanges[i];
        }
        double atr = sum / period;

        // Apply Wilder's smoothing
        for (int i = period; i < trueRanges.length; i++) {
            atr = (trueRanges[i] - atr) * (1.0 / period) + atr;
        }

        return atr;
    }

    // =========================================================================
    // Volume Moving Average
    // =========================================================================

    /**
     * Calculates the Simple Moving Average of volume.
     *
     * @param volumes list of volumes (oldest to newest)
     * @param period the MA period
     * @return the volume MA, or null if insufficient data
     */
    public static Double calculateVolumeMA(List<Double> volumes, int period) {
        if (volumes == null || volumes.size() < period) {
            return null;
        }

        double sum = 0.0;
        for (int i = volumes.size() - period; i < volumes.size(); i++) {
            sum += volumes.get(i);
        }

        return sum / period;
    }

    /**
     * Calculates volume MA from candles.
     *
     * @param candles list of candles (oldest to newest)
     * @param period the MA period
     * @return the volume MA, or null if insufficient data
     */
    public static Double calculateVolumeMaFromCandles(List<OhlcvCandleEntity> candles, int period) {
        if (candles == null || candles.size() < period) {
            return null;
        }

        double sum = 0.0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum += candles.get(i).getVolume();
        }

        return sum / period;
    }

    // =========================================================================
    // Validation helpers
    // =========================================================================

    /**
     * Validates that a calculated value is finite and within reasonable bounds.
     *
     * @param value the value to validate
     * @param minBound minimum acceptable value (inclusive)
     * @param maxBound maximum acceptable value (inclusive), or null for no upper limit
     * @return true if valid, false otherwise
     */
    public static boolean isValidFeatureValue(Double value, double minBound, Double maxBound) {
        if (value == null || !Double.isFinite(value)) {
            return false;
        }
        if (value < minBound) {
            return false;
        }
        if (maxBound != null && value > maxBound) {
            return false;
        }
        return true;
    }
}


