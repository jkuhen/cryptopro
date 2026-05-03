package com.kuhen.cryptopro.strategy;

import com.kuhen.cryptopro.config.StrategyProperties;
import com.kuhen.cryptopro.data.entity.FeaturesEntity;
import com.kuhen.cryptopro.data.entity.SignalEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Strategy engine implementing weighted multi-timeframe signal generation.
 *
 * <p>Combines trend detection from multiple timeframes with adaptive weighting:
 * <ul>
 *   <li>H1 trend: 50% weight (major trend bias)</li>
 *   <li>M15 trend: 30% weight (intermediate term)</li>
 *   <li>M5 trend: 20% weight (entry confirmation)</li>
 * </ul>
 *
 * <p>Additional filters:
 * <ul>
 *   <li>RSI filter: requires RSI between 30-70 for signal (avoid overbought/oversold)</li>
 *   <li>Volume spike detection: confirms signal strength with volume analysis</li>
 *   <li>EMA alignment: trend detection based on price vs EMA positions</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 *   MultiTimeframeStrategyEngine engine = ...;
 *   StrategySignal signal = engine.generateSignal(
 *       "BTCUSDT", h1Features, m15Features, m5Features, currentPrice
 *   );
 *   if (signal != null) {
 *       // Persist signal, generate trade
 *   }
 * </pre>
 */
@Service
public class MultiTimeframeStrategyEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiTimeframeStrategyEngine.class);

    private final StrategyProperties strategyProperties;

    /** Default constructor for framework instantiation. */
    public MultiTimeframeStrategyEngine() {
        this.strategyProperties = null;
    }

    @Autowired
    public MultiTimeframeStrategyEngine(StrategyProperties strategyProperties) {
        this.strategyProperties = strategyProperties;
    }

    /**
     * Generates a trading signal based on multi-timeframe analysis.
     *
     * @param symbol the trading symbol (e.g., "BTCUSDT")
     * @param h1Features the H1 timeframe features (50% weight)
     * @param m15Features the M15 timeframe features (30% weight)
     * @param m5Features the M5 timeframe features (20% weight)
     * @param currentPrice the current market price
     * @return a strategy signal if generated, null if no signal meet criteria
     */
    public StrategySignal generateSignal(String symbol,
                                        FeaturesEntity h1Features,
                                        FeaturesEntity m15Features,
                                        FeaturesEntity m5Features,
                                        double currentPrice) {

        // Validate inputs
        if (h1Features == null || m15Features == null || m5Features == null) {
            LOGGER.debug("Missing features for {}: h1={}, m15={}, m5={}",
                symbol, h1Features != null, m15Features != null, m5Features != null);
            return null;
        }

        // Step 1: Calculate trend scores for each timeframe based on EMA alignment
        TrendSignal h1Trend = analyzeTrendFromEma(h1Features, currentPrice, "H1");
        TrendSignal m15Trend = analyzeTrendFromEma(m15Features, currentPrice, "M15");
        TrendSignal m5Trend = analyzeTrendFromEma(m5Features, currentPrice, "M5");

        LOGGER.debug("Trend analysis for {}: H1={}, M15={}, M5={}",
            symbol, h1Trend, m15Trend, m5Trend);

        // Step 2: Apply RSI filter - signal must not be in extremes
        if (!isRsiFilterPass(m5Features.getRsi())) {
            LOGGER.info("No signal for {} - RSI gate blocked (m5Rsi={}, bounds=({}, {}))",
                    symbol,
                    formatDouble(m5Features.getRsi()),
                    formatDouble(strategyProperties.getSignal().getRsiLowerBound()),
                    formatDouble(strategyProperties.getSignal().getRsiUpperBound()));
            return null;
        }

        // Step 3: Calculate weighted composite trend score
        double h1Weight = 0.50;
        double m15Weight = 0.30;
        double m5Weight = 0.20;

        double weightedScore = (h1Weight * h1Trend.score)
            + (m15Weight * m15Trend.score)
            + (m5Weight * m5Trend.score);

        LOGGER.debug("Weighted score for {}: {} (H1:{} M15:{} M5:{})",
                symbol,
                formatDouble(weightedScore),
                formatDouble(h1Trend.score),
                formatDouble(m15Trend.score),
                formatDouble(m5Trend.score));

        // Step 4: Check for direction agreement across timeframes
        SignalDirection compositeDirection = getCompositeDirection(h1Trend, m15Trend, m5Trend);
        double disagreementPenaltyFactor = calculateDisagreementPenalty(h1Trend, m15Trend, m5Trend);

        LOGGER.debug("Composite direction for {}: {} (disagreement penalty: {})",
                symbol, compositeDirection, formatDouble(disagreementPenaltyFactor));

        // Step 5: Detect volume spike for confirmation
        VolumeSignal volumeSignal = detectVolumeSpike(m5Features.getVolumeMa(), h1Features.getVolumeMa());
        double volumeBoost = volumeSignal.isSpike ? 0.1 : 0.0;

        LOGGER.debug("Volume analysis for {}: spike={}, boost={}",
                symbol, volumeSignal.isSpike, formatDouble(volumeBoost));

        // Step 6: Calculate final confidence score
        double baseConfidence = Math.abs(weightedScore);
        double adjustedConfidence = Math.min(1.0,
            (baseConfidence * (1.0 - disagreementPenaltyFactor)) + volumeBoost);

        // Step 7: Only generate signal if confidence meets minimum threshold
        double minimumConfidence = strategyProperties.getSignal().getMinConfidence();
        if (compositeDirection == SignalDirection.NEUTRAL) {
            LOGGER.info("No signal for {} - composite direction NEUTRAL (H1={} M15={} M5={}, weightedScore={}, disagreementPenalty={})",
                    symbol,
                    h1Trend.direction,
                    m15Trend.direction,
                    m5Trend.direction,
                    formatDouble(weightedScore),
                    formatDouble(disagreementPenaltyFactor));
            return null;
        }

        if (adjustedConfidence < minimumConfidence) {
            LOGGER.info("No signal for {} - confidence below threshold (adjusted={}, min={}, weightedScore={}, disagreementPenalty={}, volumeBoost={})",
                    symbol,
                    formatDouble(adjustedConfidence),
                    formatDouble(minimumConfidence),
                    formatDouble(weightedScore),
                    formatDouble(disagreementPenaltyFactor),
                    formatDouble(volumeBoost));
            return null;
        }

        // Generate the signal
        SignalEntity.SignalTypeEnum signalType = compositeDirection == SignalDirection.LONG
            ? SignalEntity.SignalTypeEnum.BUY
            : SignalEntity.SignalTypeEnum.SELL;

        StrategySignal signal = new StrategySignal(
            symbol,
            signalType,
            compositeDirection,
            adjustedConfidence,
            new SignalRationale(h1Trend, m15Trend, m5Trend, weightedScore, disagreementPenaltyFactor, volumeSignal)
        );

        LOGGER.info("Generated {} signal for {} with confidence={}",
                signalType, symbol, formatDouble(adjustedConfidence));

        return signal;
    }

    /**
     * Analyzes trend based on EMA alignment.
     *
     * @param features the features containing EMA and price data
     * @param currentPrice the current price
     * @param timeframe the timeframe name for logging
     * @return trend signal containing direction and score
     */
    private TrendSignal analyzeTrendFromEma(FeaturesEntity features, double currentPrice, String timeframe) {
        Double ema20 = features.getEma20();
        Double ema50 = features.getEma50();
        Double ema200 = features.getEma200();

        if (ema20 == null || ema50 == null || ema200 == null) {
            LOGGER.warn("Missing EMA values for timeframe {}", timeframe);
            return new TrendSignal(SignalDirection.NEUTRAL, 0.0);
        }

        // Trend detection logic:
        // BULLISH: price > EMA20 > EMA50 > EMA200 (strong uptrend)
        // BEARISH: price < EMA20 < EMA50 < EMA200 (strong downtrend)

        double bullishScore = 0.0;
        double bearishScore = 0.0;

        // Check price vs EMA20
        if (currentPrice > ema20) {
            bullishScore += 0.3;
        } else {
            bearishScore += 0.3;
        }

        // Check EMA20 vs EMA50
        if (ema20 > ema50) {
            bullishScore += 0.3;
        } else {
            bearishScore += 0.3;
        }

        // Check EMA50 vs EMA200
        if (ema50 > ema200) {
            bullishScore += 0.4;
        } else {
            bearishScore += 0.4;
        }

        // Determine trend direction and score
        SignalDirection direction;
        double trendScore;

        if (bullishScore > bearishScore) {
            direction = SignalDirection.LONG;
            trendScore = (bullishScore - bearishScore) / 1.0; // Normalize to [-1, 1]
        } else if (bearishScore > bullishScore) {
            direction = SignalDirection.SHORT;
            trendScore = -((bearishScore - bullishScore) / 1.0);
        } else {
            direction = SignalDirection.NEUTRAL;
            trendScore = 0.0;
        }

        return new TrendSignal(direction, trendScore);
    }

    /**
     * Checks if RSI is within acceptable bounds (not overbought/oversold).
     *
     * @param rsi the RSI value
     * @return true if RSI is between lower and upper bounds
     */
    private boolean isRsiFilterPass(Double rsi) {
        if (rsi == null) {
            return false;
        }
        // Signal strength is best when RSI is not in extremes
        // Avoid: RSI > 70 (overbought) or RSI < 30 (oversold)
        double rsiLowerBound = strategyProperties.getSignal().getRsiLowerBound();
        double rsiUpperBound = strategyProperties.getSignal().getRsiUpperBound();
        return rsi > rsiLowerBound && rsi < rsiUpperBound;
    }

    /**
     * Detects if volume is significantly elevated.
     *
     * @param m5VolumeMA the M5 period volume moving average
     * @param h1VolumeMA the H1 period volume moving average
     * @return volume signal with spike detection
     */
    private VolumeSignal detectVolumeSpike(Double m5VolumeMA, Double h1VolumeMA) {
        if (m5VolumeMA == null || h1VolumeMA == null || h1VolumeMA <= 0) {
            return new VolumeSignal(false, 0.0);
        }

        // Volume spike if M5 volume is significantly higher than H1 MA
        double volumeRatio = m5VolumeMA / h1VolumeMA;
        double volumeSpikeMultiplier = strategyProperties.getSignal().getVolumeSpikeMultiplier();
        boolean isSpike = volumeRatio > volumeSpikeMultiplier;

        return new VolumeSignal(isSpike, volumeRatio);
    }

    /**
     * Determines composite direction when multiple timeframes have different signals.
     * Uses majority rule: if 2+ timeframes agree, that direction wins.
     *
     * @param h1 H1 trend signal
     * @param m15 M15 trend signal
     * @param m5 M5 trend signal
     * @return composite direction
     */
    private SignalDirection getCompositeDirection(TrendSignal h1, TrendSignal m15, TrendSignal m5) {
        int longCount = 0;
        int shortCount = 0;

        if (h1.direction == SignalDirection.LONG) longCount++;
        else if (h1.direction == SignalDirection.SHORT) shortCount++;

        if (m15.direction == SignalDirection.LONG) longCount++;
        else if (m15.direction == SignalDirection.SHORT) shortCount++;

        if (m5.direction == SignalDirection.LONG) longCount++;
        else if (m5.direction == SignalDirection.SHORT) shortCount++;

        if (longCount >= 2) return SignalDirection.LONG;
        if (shortCount >= 2) return SignalDirection.SHORT;
        return SignalDirection.NEUTRAL;
    }

    /**
     * Calculates a penalty when timeframes disagree on direction.
     *
     * @param h1 H1 trend
     * @param m15 M15 trend
     * @param m5 M5 trend
     * @return penalty factor (0.0 = full signal strength, up to 1.0 = heavily penalized)
     */
    private double calculateDisagreementPenalty(TrendSignal h1, TrendSignal m15, TrendSignal m5) {
        double baseDisagreementPenalty = strategyProperties.getSignal().getDisagreementPenalty();
        double penalty = 0.0;

        // Penalize if H1 and M15 disagree (major components)
        if (h1.direction != SignalDirection.NEUTRAL && m15.direction != SignalDirection.NEUTRAL &&
            h1.direction != m15.direction) {
            penalty += baseDisagreementPenalty * 0.4;
        }

        // Penalize if M15 and M5 disagree (intermediate and entry)
        if (m15.direction != SignalDirection.NEUTRAL && m5.direction != SignalDirection.NEUTRAL &&
            m15.direction != m5.direction) {
            penalty += baseDisagreementPenalty * 0.3;
        }

        // Penalize if H1 and M5 disagree (major and entry)
        if (h1.direction != SignalDirection.NEUTRAL && m5.direction != SignalDirection.NEUTRAL &&
            h1.direction != m5.direction) {
            penalty += baseDisagreementPenalty * 0.2;
        }

        return Math.min(1.0, penalty);
    }

    /**
     * Record representing a trend signal from a single timeframe.
     */
    public record TrendSignal(
        SignalDirection direction,
        double score
    ) {}

    /**
     * Record representing volume spike analysis.
     */
    public record VolumeSignal(
        boolean isSpike,
        double volumeRatio
    ) {}

    /**
     * Record containing the details and rationale of a generated strategy signal.
     */
    public record SignalRationale(
        TrendSignal h1Trend,
        TrendSignal m15Trend,
        TrendSignal m5Trend,
        double weightedScore,
        double disagreementPenalty,
        VolumeSignal volumeSignal
    ) {
        @Override
        public String toString() {
            return "SignalRationale{" +
                "H1=" + h1Trend.direction + "(" + String.format("%.3f", h1Trend.score) + ")" +
                ", M15=" + m15Trend.direction + "(" + String.format("%.3f", m15Trend.score) + ")" +
                ", M5=" + m5Trend.direction + "(" + String.format("%.3f", m5Trend.score) + ")" +
                ", weighted=" + String.format("%.3f", weightedScore) +
                ", disagreement=" + String.format("%.3f", disagreementPenalty) +
                ", volume_spike=" + volumeSignal.isSpike +
                "}";
        }
    }

    private static String formatDouble(Double value) {
        if (value == null) {
            return "null";
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }
}

