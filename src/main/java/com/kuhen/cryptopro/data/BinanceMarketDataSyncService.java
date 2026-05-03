package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.config.BinanceProperties;
import com.kuhen.cryptopro.data.dto.BinanceMarketSyncResultDto;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.FundingRate;
import com.kuhen.cryptopro.data.model.OpenInterestSnapshot;
import com.kuhen.cryptopro.data.model.Timeframe;
import com.kuhen.cryptopro.strategy.SignalGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coordinates scheduled Binance candle sync for the supported symbols.
 *
 * <p>Primary source: the live M1 WebSocket cache / exchange-backed provider.
 * If the cache does not yet contain enough data the provider transparently falls back
 * to the Binance REST klines endpoint with retry and rate-limit handling.
 *
 * <p>Every sync cycle:
 * <ol>
 *     <li>Loads the latest M1 candles from Binance/cache.</li>
 *     <li>Persists closed M1 candles into PostgreSQL.</li>
 *     <li>Aggregates those M1 candles into closed M5/M15/H1 candles.</li>
 *     <li>Persists the aggregated candles via the same JPA upsert path.</li>
 * </ol>
 */
@Service
@ConditionalOnProperty(prefix = "cryptopro.data", name = "provider", havingValue = "binance")
public class BinanceMarketDataSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BinanceMarketDataSyncService.class);
    private static final List<Timeframe> HIGHER_TIMEFRAMES = List.of(Timeframe.M5, Timeframe.M15, Timeframe.H1);
    /** Minimum candles required to compute all indicators (EMA200 needs 200). */
    private static final int FEATURE_MIN_CANDLES = 220;

    private final BinanceMarketDataProvider marketDataProvider;
    private final CandlePersistenceService candlePersistenceService;
    private final DerivativesDataPersistenceService derivativesDataPersistenceService;
    private final FeatureEngineeringService featureEngineeringService;
    private final SignalGenerationService signalGenerationService;
    private final BinanceProperties binanceProperties;
    private final Clock clock;

    @Autowired
    public BinanceMarketDataSyncService(BinanceMarketDataProvider marketDataProvider,
                                        CandlePersistenceService candlePersistenceService,
                                        DerivativesDataPersistenceService derivativesDataPersistenceService,
                                        FeatureEngineeringService featureEngineeringService,
                                        SignalGenerationService signalGenerationService,
                                        BinanceProperties binanceProperties) {
        this(marketDataProvider, candlePersistenceService, derivativesDataPersistenceService,
                featureEngineeringService, signalGenerationService, binanceProperties, Clock.systemUTC());
    }

    BinanceMarketDataSyncService(BinanceMarketDataProvider marketDataProvider,
                                 CandlePersistenceService candlePersistenceService,
                                 DerivativesDataPersistenceService derivativesDataPersistenceService,
                                 FeatureEngineeringService featureEngineeringService,
                                 SignalGenerationService signalGenerationService,
                                 BinanceProperties binanceProperties,
                                 Clock clock) {
        this.marketDataProvider = marketDataProvider;
        this.candlePersistenceService = candlePersistenceService;
        this.derivativesDataPersistenceService = derivativesDataPersistenceService;
        this.featureEngineeringService = featureEngineeringService;
        this.signalGenerationService = signalGenerationService;
        this.binanceProperties = binanceProperties;
        this.clock = clock;
    }

    public List<BinanceMarketSyncResultDto> syncConfiguredSymbols() {
        List<String> symbols = binanceProperties.getWebsocketSymbols();
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }

        List<BinanceMarketSyncResultDto> results = new ArrayList<>(symbols.size());
        for (String symbol : symbols) {
            results.add(syncSymbol(symbol));
        }
        return List.copyOf(results);
    }

    public BinanceMarketSyncResultDto syncSymbol(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        int lookback = Math.max(60, binanceProperties.getSyncLookbackCandles());
        Instant syncedAt = Instant.now(clock);

        List<Candle> recentM1 = marketDataProvider.getRecentCandlesFromExchange(normalizedSymbol, Timeframe.M1, lookback);
        if (recentM1.isEmpty()) {
            LOGGER.warn("Skipping sync for {} because no exchange-backed M1 candles were available.", normalizedSymbol);
            return new BinanceMarketSyncResultDto(normalizedSymbol, syncedAt, 0, 0, 0, 0,
                    "No exchange candles available from WebSocket cache or REST backfill");
        }

        List<Candle> closedM1 = recentM1.stream()
                .filter(candle -> isClosed(candle, Timeframe.M1, syncedAt))
                .sorted(Comparator.comparing(Candle::openTime))
                .toList();

        int persistedM1 = candlePersistenceService.saveClosedCandles(deduplicate(closedM1));
        int persistedM5 = candlePersistenceService.saveClosedCandles(aggregateClosedCandles(closedM1, Timeframe.M5, syncedAt));
        int persistedM15 = candlePersistenceService.saveClosedCandles(aggregateClosedCandles(closedM1, Timeframe.M15, syncedAt));
        int persistedH1 = candlePersistenceService.saveClosedCandles(aggregateClosedCandles(closedM1, Timeframe.H1, syncedAt));

        FundingRate fundingRate = marketDataProvider.getLatestFundingRate(normalizedSymbol);
        OpenInterestSnapshot latestOpenInterest = marketDataProvider.getRecentOpenInterest(normalizedSymbol, 1)
                .stream()
                .max(Comparator.comparing(OpenInterestSnapshot::timestamp))
                .orElse(null);
        logDerivativesAnomalies(normalizedSymbol, syncedAt, latestOpenInterest, fundingRate);
        boolean derivativesPersisted = derivativesDataPersistenceService.persistSnapshot(
                normalizedSymbol,
                syncedAt,
                latestOpenInterest,
                fundingRate
        );

        String notes = derivativesPersisted
                ? "Persisted candles plus normalized derivatives_data snapshot"
                : "Persisted candles only; derivatives_data snapshot skipped due to missing/invalid data";
        LOGGER.info("Binance sync completed for {} -> M1={}, M5={}, M15={}, H1={}",
                normalizedSymbol, persistedM1, persistedM5, persistedM15, persistedH1);

        // Step 2: Run feature engineering for each timeframe (M5, M15, H1)
        runFeatureEngineering(normalizedSymbol);

        // Step 3: Generate signals from the freshly computed features
        runSignalGeneration(normalizedSymbol, syncedAt);

        return new BinanceMarketSyncResultDto(normalizedSymbol, syncedAt, persistedM1, persistedM5, persistedM15, persistedH1, notes);
    }

    private void runFeatureEngineering(String symbol) {
        Long instrumentId = featureEngineeringService.findInstrumentIdPublic(symbol);
        if (instrumentId == null) {
            LOGGER.warn("Cannot run feature engineering for {}: instrument not found", symbol);
            return;
        }
        for (String tf : List.of("M5", "M15", "H1")) {
            try {
                boolean persisted = featureEngineeringService.calculateAndPersistFeatures(
                        instrumentId, symbol, tf, FEATURE_MIN_CANDLES);
                if (persisted) {
                    LOGGER.debug("Features computed and persisted for {} / {}", symbol, tf);
                }
            } catch (Exception ex) {
                LOGGER.error("Feature engineering failed for {} / {}: {}", symbol, tf, ex.getMessage(), ex);
            }
        }
    }

    private void runSignalGeneration(String symbol, Instant syncedAt) {
        try {
            signalGenerationService.generateAndPersistSignal(symbol, syncedAt);
        } catch (Exception ex) {
            LOGGER.error("Signal generation failed for {}: {}", symbol, ex.getMessage(), ex);
        }
    }

    List<Candle> aggregateClosedCandles(List<Candle> closedM1Candles, Timeframe targetTimeframe, Instant syncedAt) {
        if (targetTimeframe == Timeframe.M1 || closedM1Candles.isEmpty()) {
            return List.of();
        }

        Map<Instant, List<Candle>> grouped = new LinkedHashMap<>();
        for (Candle candle : closedM1Candles.stream().sorted(Comparator.comparing(Candle::openTime)).toList()) {
            Instant bucketStart = bucketStart(candle.openTime(), targetTimeframe);
            grouped.computeIfAbsent(bucketStart, ignored -> new ArrayList<>()).add(candle);
        }

        List<Candle> aggregated = new ArrayList<>();
        for (Map.Entry<Instant, List<Candle>> entry : grouped.entrySet()) {
            Instant bucketStart = entry.getKey();
            List<Candle> bucketCandles = entry.getValue();

            if (!isWindowClosed(bucketStart, targetTimeframe, syncedAt)) {
                continue;
            }
            if (!isCompleteWindow(bucketCandles, bucketStart, targetTimeframe)) {
                continue;
            }

            aggregated.add(aggregateWindow(bucketCandles, targetTimeframe, bucketStart));
        }
        return deduplicate(aggregated);
    }

    private Candle aggregateWindow(List<Candle> bucketCandles, Timeframe targetTimeframe, Instant bucketStart) {
        List<Candle> sorted = bucketCandles.stream()
                .sorted(Comparator.comparing(Candle::openTime))
                .toList();
        Candle first = sorted.get(0);
        Candle last = sorted.get(sorted.size() - 1);

        double high = sorted.stream().mapToDouble(Candle::high).max().orElse(first.high());
        double low = sorted.stream().mapToDouble(Candle::low).min().orElse(first.low());
        double volume = sorted.stream().mapToDouble(Candle::volume).sum();

        return new Candle(
                first.symbol(),
                targetTimeframe,
                bucketStart,
                first.open(),
                high,
                low,
                last.close(),
                volume
        );
    }

    private boolean isCompleteWindow(List<Candle> bucketCandles, Instant bucketStart, Timeframe targetTimeframe) {
        int expectedCount = (int) (targetTimeframe.getDuration().toMinutes() / Timeframe.M1.getDuration().toMinutes());
        if (bucketCandles.size() != expectedCount) {
            return false;
        }

        Instant expectedOpenTime = bucketStart;
        Duration step = Timeframe.M1.getDuration();
        for (Candle candle : bucketCandles.stream().sorted(Comparator.comparing(Candle::openTime)).toList()) {
            if (!candle.openTime().equals(expectedOpenTime)) {
                return false;
            }
            expectedOpenTime = expectedOpenTime.plus(step);
        }
        return true;
    }

    private boolean isClosed(Candle candle, Timeframe timeframe, Instant referenceTime) {
        return !candle.openTime().plus(timeframe.getDuration()).isAfter(referenceTime.truncatedTo(ChronoUnit.MINUTES));
    }

    private boolean isWindowClosed(Instant bucketStart, Timeframe timeframe, Instant referenceTime) {
        return !bucketStart.plus(timeframe.getDuration()).isAfter(referenceTime.truncatedTo(ChronoUnit.MINUTES));
    }

    private Instant bucketStart(Instant openTime, Timeframe timeframe) {
        ZonedDateTime utc = openTime.atZone(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MINUTES);

        return switch (timeframe) {
            case M1 -> utc.toInstant();
            case M5 -> utc.withMinute((utc.getMinute() / 5) * 5).withSecond(0).withNano(0).toInstant();
            case M15 -> utc.withMinute((utc.getMinute() / 15) * 15).withSecond(0).withNano(0).toInstant();
            case H1 -> utc.withMinute(0).withSecond(0).withNano(0).toInstant();
        };
    }

    private List<Candle> deduplicate(List<Candle> candles) {
        Map<String, Candle> unique = new LinkedHashMap<>();
        for (Candle candle : candles) {
            unique.put(candle.symbol() + '|' + candle.timeframe().name() + '|' + candle.openTime(), candle);
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(Candle::openTime))
                .toList();
    }

    private String normalizeSymbol(String symbol) {
        String normalized = String.valueOf(symbol).toUpperCase();
        return binanceProperties.getSymbolMap().getOrDefault(normalized, normalized);
    }

    private void logDerivativesAnomalies(String symbol,
                                         Instant syncedAt,
                                         OpenInterestSnapshot openInterest,
                                         FundingRate fundingRate) {
        if (openInterest == null) {
            LOGGER.warn("Open-interest snapshot missing for {} at {}.", symbol, syncedAt);
        } else if (Duration.between(openInterest.timestamp(), syncedAt).toMinutes() > 30) {
            LOGGER.warn("Open-interest snapshot for {} is stale (snapshot={}, sync={}).",
                    symbol, openInterest.timestamp(), syncedAt);
        }

        if (fundingRate == null || !Double.isFinite(fundingRate.rate())) {
            LOGGER.warn("Funding-rate snapshot missing/invalid for {} at {}.", symbol, syncedAt);
        } else if (Duration.between(fundingRate.fundingTime(), syncedAt).toHours() > 12) {
            LOGGER.warn("Funding-rate snapshot for {} is stale (fundingTime={}, sync={}).",
                    symbol, fundingRate.fundingTime(), syncedAt);
        }
    }
}


