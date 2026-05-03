package com.kuhen.cryptopro.strategy;

import com.kuhen.cryptopro.data.SignalPersistenceService;
import com.kuhen.cryptopro.config.StrategyProperties;
import com.kuhen.cryptopro.data.entity.FeaturesEntity;
import com.kuhen.cryptopro.data.entity.OhlcvCandleEntity;
import com.kuhen.cryptopro.data.entity.SignalEntity;
import com.kuhen.cryptopro.data.repository.FeaturesRepository;
import com.kuhen.cryptopro.data.repository.OhlcvCandleRepository;
import com.kuhen.cryptopro.config.TradeLifecycleProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service integrating multi-timeframe strategy signal generation with market data.
 *
 * <p>Orchestrates signal generation by:
 * <ol>
 *   <li>Fetching latest features for H1, M15, M5 timeframes</li>
 *   <li>Retrieving current candle price</li>
 *   <li>Running multi-timeframe strategy engine</li>
 *   <li>Persisting generated signals to database</li>
 * </ol>
 *
 * <p>Called by {@code BinanceMarketDataSyncService} after each candle sync cycle.
 */
@Service
public class SignalGenerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignalGenerationService.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final MultiTimeframeStrategyEngine strategyEngine;
    private final SignalPersistenceService signalPersistenceService;
    private final OhlcvCandleRepository candleRepository;
    private final FeaturesRepository featuresRepository;
    private final TradeLifecycleProperties tradeLifecycleProperties;
    private final StrategyProperties strategyProperties;

    /** Default constructor for framework instantiation. */
    public SignalGenerationService() {
        this.strategyEngine = null;
        this.signalPersistenceService = null;
        this.candleRepository = null;
        this.featuresRepository = null;
        this.tradeLifecycleProperties = null;
        this.strategyProperties = null;
    }

    @Autowired
    public SignalGenerationService(
            MultiTimeframeStrategyEngine strategyEngine,
            SignalPersistenceService signalPersistenceService,
            OhlcvCandleRepository candleRepository,
            FeaturesRepository featuresRepository,
            TradeLifecycleProperties tradeLifecycleProperties,
            StrategyProperties strategyProperties) {
        this.strategyEngine = strategyEngine;
        this.signalPersistenceService = signalPersistenceService;
        this.candleRepository = candleRepository;
        this.featuresRepository = featuresRepository;
        this.tradeLifecycleProperties = tradeLifecycleProperties;
        this.strategyProperties = strategyProperties;
    }

    /**
     * Generates and persists a trading signal for a given symbol.
     *
     * <p>Fetches the latest features for H1, M15, M5 candles directly by timeframe,
     * runs the multi-timeframe strategy engine, and persists any generated signals.
     *
     * @param symbol the trading symbol (e.g., "BTCUSDT")
     * @param recordedAt the timestamp for signal generation (typically current time)
     * @return the generated signal if one was created, null otherwise
     */
    @Transactional
    public SignalEntity generateAndPersistSignal(String symbol, Instant recordedAt) {
        // Step 1: Look up instrument ID
        Long instrumentId = findInstrumentId(symbol);
        if (instrumentId == null) {
            LOGGER.warn("Instrument not found for symbol: {}", symbol);
            return null;
        }

        // Step 2: Fetch the latest features for each timeframe directly by timeframe column
        FeaturesEntity h1Features = featuresRepository.findLatestByTimeframe(instrumentId, "H1").orElse(null);
        FeaturesEntity m15Features = featuresRepository.findLatestByTimeframe(instrumentId, "M15").orElse(null);
        FeaturesEntity m5Features = featuresRepository.findLatestByTimeframe(instrumentId, "M5").orElse(null);

        if (h1Features == null || m15Features == null || m5Features == null) {
            LOGGER.info("No signal for {} at {} - missing features (H1={}, M15={}, M5={})",
                symbol, recordedAt, h1Features != null, m15Features != null, m5Features != null);
            return null;
        }

        if (strategyProperties != null && strategyProperties.getSignal() != null && m5Features.getRsi() != null) {
            double lower = strategyProperties.getSignal().getRsiLowerBound();
            double upper = strategyProperties.getSignal().getRsiUpperBound();
            double rsi = m5Features.getRsi();
            if (rsi <= lower || rsi >= upper) {
                LOGGER.info("No signal for {} at {} - RSI filter blocked (rsi={}, bounds=({}, {}))",
                        symbol, recordedAt, String.format("%.3f", rsi), lower, upper);
            }
        }

        // Step 3: Get current price from latest M5 candle
        List<OhlcvCandleEntity> latestCandles = candleRepository.findLatest(symbol, "M5", 1);
        if (latestCandles.isEmpty()) {
            LOGGER.warn("No recent candles found for {}", symbol);
            return null;
        }

        double currentPrice = latestCandles.get(0).getClosePrice();

        // Step 4: Generate signal via strategy engine
        StrategySignal signal = strategyEngine.generateSignal(
            symbol,
            h1Features,
            m15Features,
            m5Features,
            currentPrice
        );

        if (signal == null) {
            double minConfidence = (strategyProperties != null && strategyProperties.getSignal() != null)
                    ? strategyProperties.getSignal().getMinConfidence()
                    : 0.0;
            LOGGER.info("No signal generated for {} at {} - strategy returned null (minConfidence={}, see strategy-gate logs for exact reason)",
                    symbol, recordedAt, String.format("%.3f", minConfidence));
            return null;
        }

        // Step 5: Compute projected Entry / SL / TP from current price and ATR
        Double atr = m5Features.getAtr();
        Double entryPrice = null;
        Double stopLoss   = null;
        Double takeProfit = null;
        if (atr != null && atr > 0.0 && tradeLifecycleProperties != null) {
            boolean isBuy = signal.signalType() == SignalEntity.SignalTypeEnum.BUY;
            double slMultiplier = tradeLifecycleProperties.getTrailingStopAtrMultiplier();
            double tpMultiplier = tradeLifecycleProperties.getTakeProfitAtrMultiplier();
            entryPrice = currentPrice;
            stopLoss   = isBuy ? currentPrice - atr * slMultiplier
                               : currentPrice + atr * slMultiplier;
            if (tpMultiplier > 0.0) {
                takeProfit = isBuy ? currentPrice + atr * tpMultiplier
                                   : currentPrice - atr * tpMultiplier;
            }
        }

        // Step 6: Persist signal and return
        if (signalPersistenceService.isDuplicateAtMinute(instrumentId, SignalEntity.TimeframeEnum.M5, recordedAt)) {
            LOGGER.info("No signal persisted for {} at {} - duplicate-at-minute for instrumentId={} timeframe=M5",
                    symbol, recordedAt, instrumentId);
            return null;
        }

        SignalEntity persisted = signalPersistenceService.persistSignal(
            instrumentId,
            SignalEntity.TimeframeEnum.M5, // Signals triggered on M5 entry signal
            signal.signalType(),
            signal.confidenceScore(),
            recordedAt,
            entryPrice,
            stopLoss,
            takeProfit
        );

        if (persisted != null) {
            LOGGER.info("Signal persisted for {} - Type: {}, Confidence: {}%, Rationale: {}",
                symbol, signal.signalType(),
                String.format("%.3f", signal.confidenceScore() * 100),
                signal.rationale());
        } else {
            LOGGER.info("No signal persisted for {} at {} - persistSignal returned null", symbol, recordedAt);
        }

        return persisted;
    }

    /**
     * Generates signals for multiple symbols.
     *
     * @param symbols list of symbols to process
     * @param recordedAt timestamp for signal generation
     * @return count of signals successfully generated and persisted
     */
    @Transactional
    public int generateSignalsForSymbols(List<String> symbols, Instant recordedAt) {
        int count = 0;
        for (String symbol : symbols) {
            try {
                SignalEntity signal = generateAndPersistSignal(symbol, recordedAt);
                if (signal != null) {
                    count++;
                }
            } catch (Exception ex) {
                LOGGER.error("Error generating signal for {}: {}", symbol, ex.getMessage(), ex);
            }
        }
        return count;
    }

    /**
     * Retrieves recent signals for a symbol.
     *
     * @param symbol the trading symbol
     * @param limit maximum number of signals to return
     * @return list of recent signals
     */
    public List<SignalEntity> getRecentSignals(String symbol, int limit) {
        Long instrumentId = findInstrumentId(symbol);
        if (instrumentId == null) {
            return List.of();
        }
        return signalPersistenceService.getRecentSignals(instrumentId, limit);
    }

    /**
     * Retrieves signals for a symbol within a time range.
     *
     * @param symbol the trading symbol
     * @param fromTime inclusive start time
     * @param toTime exclusive end time
     * @return list of signals in the time range
     */
    public List<SignalEntity> getSignalsInRange(String symbol, Instant fromTime, Instant toTime) {
        Long instrumentId = findInstrumentId(symbol);
        if (instrumentId == null) {
            return List.of();
        }
        return signalPersistenceService.getSignalsInRange(instrumentId, fromTime, toTime);
    }

    /**
     * Counts generated signals by type.
     *
     * @param signalType BUY or SELL
     * @param fromTime inclusive start time
     * @param toTime exclusive end time
     * @return count of signals
     */
    public int countSignalsByType(String signalType, Instant fromTime, Instant toTime) {
        return signalPersistenceService.countSignalsByType(signalType, fromTime, toTime);
    }

    /**
     * Looks up the instrument ID for a symbol.
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
            LOGGER.debug("Could not find instrument for symbol: {}", symbol);
            return null;
        }
    }
}

