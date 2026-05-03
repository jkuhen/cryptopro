package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.data.entity.FeaturesEntity;
import com.kuhen.cryptopro.data.entity.OhlcvCandleEntity;
import com.kuhen.cryptopro.data.repository.FeaturesRepository;
import com.kuhen.cryptopro.data.repository.OhlcvCandleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for calculating and persisting technical features (EMA, RSI, ATR, Volume MA).
 *
 * <p>Provides efficient incremental feature calculation to avoid recomputing
 * the entire dataset. Features are calculated from OHLCV candle data and
 * persisted to the features table.
 *
 * <p>Key design patterns:
 * <ul>
 *   <li>Incremental calculation: Only new/updated candles are processed</li>
 *   <li>Lazy initialization: Feature calculations bootstrap from historical data</li>
 *   <li>Upsert semantics: Late-arriving corrections are handled gracefully</li>
 * </ul>
 */
@Service
public class FeatureEngineeringService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureEngineeringService.class);

    // Default periods for technical indicators
    private static final int EMA_20_PERIOD = 20;
    private static final int EMA_50_PERIOD = 50;
    private static final int EMA_200_PERIOD = 200;
    private static final int RSI_PERIOD = 14;
    private static final int ATR_PERIOD = 14;
    private static final int VOLUME_MA_PERIOD = 20;

    @PersistenceContext
    private EntityManager entityManager;

    private final OhlcvCandleRepository candleRepository;
    private final FeaturesRepository featuresRepository;

    public FeatureEngineeringService(
            OhlcvCandleRepository candleRepository,
            FeaturesRepository featuresRepository) {
        this.candleRepository = candleRepository;
        this.featuresRepository = featuresRepository;
    }

    /**
     * Calculates and persists features for the latest candle of a given symbol and timeframe.
     *
     * <p>This method efficiently handles incremental updates by only processing
     * newly available candles while reusing previously calculated features.
     *
     * @param symbolId the instrument ID to calculate features for
     * @param symbol the symbol name (e.g., "BTCUSDT")
     * @param timeframe the timeframe identifier (e.g., "M1", "H1")
     * @param minCandlesRequired minimum candles required for full feature set
     * @return true if features were successfully calculated and persisted, false otherwise
     */
    @Transactional
    public boolean calculateAndPersistFeatures(Long symbolId, String symbol, String timeframe, int minCandlesRequired) {
        // Find the latest candle for this symbol/timeframe
        List<OhlcvCandleEntity> latestCandles = candleRepository.findLatest(symbol, timeframe, 1);
        if (latestCandles.isEmpty()) {
            LOGGER.debug("No candles found for {} / {}", symbol, timeframe);
            return false;
        }

        OhlcvCandleEntity latestCandle = latestCandles.get(0);
        Instant recordedAt = latestCandle.getOpenTime().truncatedTo(ChronoUnit.MINUTES);

        // Check if features were already calculated for this timestamp and timeframe
        Optional<FeaturesEntity> existing = featuresRepository.findLatestBefore(symbolId, timeframe, recordedAt);
        if (existing.isPresent() && existing.get().getRecordedAt().equals(recordedAt)) {
            LOGGER.debug("Features for {} / {} at {} already exist; skipping", symbol, timeframe, recordedAt);
            return false;
        }

        // Gather historical candles for feature calculation
        List<OhlcvCandleEntity> historicalCandles = candleRepository.findLatest(symbol, timeframe, minCandlesRequired);
        if (historicalCandles.isEmpty()) {
            LOGGER.warn("Insufficient candle data for feature calculation: {}", symbol);
            return false;
        }

        // Reverse to oldest-to-newest order (expected by calculation methods)
        Collections.reverse(historicalCandles);

        // Calculate features
        CalculatedFeatures features = calculateFeatures(historicalCandles);
        if (features == null || !features.isValid()) {
            LOGGER.warn("Feature calculation failed for {}: {}", symbol, features);
            return false;
        }

        // Persist the features with the timeframe
        persistFeatures(symbolId, timeframe, recordedAt, features);
        LOGGER.debug("Features persisted for {} / {} at {}: {}", symbol, timeframe, recordedAt, features);

        return true;
    }

    /**
     * Calculates features for all symbols and timeframes (full batch recalculation).
     *
     * <p>Use this for bulk updates when needed, but prefer {@link #calculateAndPersistFeatures(Long, String, String, int)}
     * for incremental updates.
     *
     * @param symbols list of symbols to process
     * @param timeframes list of timeframes to process
     * @param minCandlesRequired minimum candles required
     * @return count of successfully persisted feature rows
     */
    @Transactional
    public int calculateAndPersistFeaturesForAll(List<String> symbols, List<String> timeframes, int minCandlesRequired) {
        int count = 0;
        for (String symbol : symbols) {
            for (String timeframe : timeframes) {
                // Lookup instrument_id from symbol
                Long symbolId = findInstrumentId(symbol);
                if (symbolId == null) {
                    LOGGER.warn("No instrument found for symbol: {}", symbol);
                    continue;
                }
                if (calculateAndPersistFeatures(symbolId, symbol, timeframe, minCandlesRequired)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Calculates features from a list of candles.
     *
     * @param candles list of candles (oldest to newest)
     * @return calculated features, or null if calculation failed
     */
    public CalculatedFeatures calculateFeatures(List<OhlcvCandleEntity> candles) {
        if (candles == null || candles.isEmpty()) {
            return null;
        }

        // Extract price and volume lists
        List<Double> closes = candles.stream()
                .map(OhlcvCandleEntity::getClosePrice)
                .collect(Collectors.toList());

        List<Double> volumes = candles.stream()
                .map(OhlcvCandleEntity::getVolume)
                .collect(Collectors.toList());

        List<Double> highs = candles.stream()
                .map(OhlcvCandleEntity::getHighPrice)
                .collect(Collectors.toList());

        List<Double> lows = candles.stream()
                .map(OhlcvCandleEntity::getLowPrice)
                .collect(Collectors.toList());

        // Calculate EMA values
        Double ema20 = FeatureEngineeringUtil.calculateEma(closes, EMA_20_PERIOD);
        Double ema50 = FeatureEngineeringUtil.calculateEma(closes, EMA_50_PERIOD);
        Double ema200 = FeatureEngineeringUtil.calculateEma(closes, EMA_200_PERIOD);

        // Calculate RSI
        Double rsi = FeatureEngineeringUtil.calculateRsi(closes, RSI_PERIOD);

        // Calculate ATR
        Double atr = FeatureEngineeringUtil.calculateAtrFromPrices(highs, lows, closes, ATR_PERIOD);

        // Calculate Volume MA
        Double volumeMa = FeatureEngineeringUtil.calculateVolumeMA(volumes, VOLUME_MA_PERIOD);

        return new CalculatedFeatures(ema20, ema50, ema200, rsi, atr, volumeMa);
    }

    /**
     * Persists calculated features using upsert semantics.
     *
     * @param symbolId the instrument ID
     * @param recordedAt the timestamp of the feature row
     * @param features the calculated features
     */
    @Transactional
    public void persistFeatures(Long symbolId, String timeframe, Instant recordedAt, CalculatedFeatures features) {
        if (features == null || !features.isValid()) {
            LOGGER.warn("Cannot persist invalid features: {}", features);
            return;
        }

        featuresRepository.upsert(
                symbolId,
                null, // signal_id (optional, not set here)
                timeframe,
                recordedAt,
                features.ema20(),
                features.ema50(),
                features.ema200(),
                features.rsi(),
                features.atr(),
                features.volumeMa()
        );
    }

    /**
     * Retrieves the most recent features for a given instrument.
     *
     * @param symbolId the instrument ID
     * @param limit the maximum number of feature rows to return
     * @return list of features (oldest to newest)
     */
    public List<FeaturesEntity> getLatestFeatures(Long symbolId, int limit) {
        List<FeaturesEntity> features = featuresRepository.findLatest(symbolId, limit);
        Collections.reverse(features); // Reverse to oldest-to-newest
        return features;
    }

    /**
     * Retrieves features for a time range.
     *
     * @param symbolId the instrument ID
     * @param fromTime inclusive start time
     * @param toTime exclusive end time
     * @return list of features in the time range
     */
    public List<FeaturesEntity> getFeaturesInRange(Long symbolId, Instant fromTime, Instant toTime) {
        return featuresRepository.findRange(symbolId, fromTime, toTime);
    }

    /**
     * Public wrapper for instrument ID lookup – used by callers that need to
     * resolve an instrument before calling {@link #calculateAndPersistFeatures}.
     */
    public Long findInstrumentIdPublic(String symbol) {
        return findInstrumentId(symbol);
    }

    /**
     * Looks up the instrument ID for a given symbol via native query.
     *
     * @param symbol the symbol name
     * @return the instrument ID, or null if not found
     */
    private Long findInstrumentId(String symbol) {
        try {
            Object result = entityManager.createNativeQuery("""
                    SELECT id FROM instrument
                    WHERE exchange_name = 'BINANCE' AND symbol = ?1
                    LIMIT 1
                    """)
                    .setParameter(1, symbol)
                    .getSingleResult();
            return ((Number) result).longValue();
        } catch (Exception ex) {
            LOGGER.debug("Could not find instrument for symbol: {}", symbol, ex);
            return null;
        }
    }

    /**
     * Record containing calculated feature values.
     */
    public record CalculatedFeatures(
            Double ema20,
            Double ema50,
            Double ema200,
            Double rsi,
            Double atr,
            Double volumeMa
    ) {
        /**
         * Validates that all features are finite and within expected ranges.
         *
         * @return true if all features are valid
         */
        public boolean isValid() {
            return FeatureEngineeringUtil.isValidFeatureValue(ema20, 0.0, null) &&
                    FeatureEngineeringUtil.isValidFeatureValue(ema50, 0.0, null) &&
                    FeatureEngineeringUtil.isValidFeatureValue(ema200, 0.0, null) &&
                    FeatureEngineeringUtil.isValidFeatureValue(rsi, 0.0, 100.0) &&
                    FeatureEngineeringUtil.isValidFeatureValue(atr, 0.0, null) &&
                    FeatureEngineeringUtil.isValidFeatureValue(volumeMa, 0.0, null);
        }

        @Override
        public String toString() {
            return "CalculatedFeatures{" +
                    "ema20=" + ema20 +
                    ", ema50=" + ema50 +
                    ", ema200=" + ema200 +
                    ", rsi=" + rsi +
                    ", atr=" + atr +
                    ", volumeMa=" + volumeMa +
                    '}';
        }
    }
}

