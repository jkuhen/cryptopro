package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.data.entity.OhlcvCandleEntity;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.Timeframe;
import com.kuhen.cryptopro.data.repository.OhlcvCandleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

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
 * One-shot historical backfill that pages Binance M1 klines and builds M5/M15/H1 candles.
 */
@Service
@ConditionalOnExpression("'${cryptopro.data.provider:}'=='binance' and '${cryptopro.backfill.binance.enabled:false}'=='true'")
public class BinanceHistoricalBackfillService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BinanceHistoricalBackfillService.class);
    private static final int FEATURE_MIN_CANDLES = 220;

    private final BinanceMarketDataProvider marketDataProvider;
    private final CandlePersistenceService candlePersistenceService;
    private final OhlcvCandleRepository ohlcvCandleRepository;
    private final FeatureEngineeringService featureEngineeringService;

    public BinanceHistoricalBackfillService(
            BinanceMarketDataProvider marketDataProvider,
            CandlePersistenceService candlePersistenceService,
            OhlcvCandleRepository ohlcvCandleRepository,
            FeatureEngineeringService featureEngineeringService
    ) {
        this.marketDataProvider = marketDataProvider;
        this.candlePersistenceService = candlePersistenceService;
        this.ohlcvCandleRepository = ohlcvCandleRepository;
        this.featureEngineeringService = featureEngineeringService;
    }

    public BackfillSummary backfill(
            List<String> symbols,
            Instant startTime,
            Instant endTime,
            int pageSize,
            long sleepMs
    ) {
        List<SymbolBackfillResult> symbolResults = new ArrayList<>();
        int totalM1 = 0;
        int totalM5 = 0;
        int totalM15 = 0;
        int totalH1 = 0;

        for (String symbolRaw : symbols) {
            String symbol = String.valueOf(symbolRaw).trim().toUpperCase();
            if (symbol.isBlank()) {
                continue;
            }

            SymbolBackfillResult result = backfillSingleSymbol(symbol, startTime, endTime, pageSize, sleepMs);
            symbolResults.add(result);
            totalM1 += result.persistedM1();
            totalM5 += result.persistedM5();
            totalM15 += result.persistedM15();
            totalH1 += result.persistedH1();
        }

        return new BackfillSummary(symbolResults, totalM1, totalM5, totalM15, totalH1);
    }

    private SymbolBackfillResult backfillSingleSymbol(
            String symbol,
            Instant startTime,
            Instant endTime,
            int pageSize,
            long sleepMs
    ) {
        Instant cursor = startTime.truncatedTo(ChronoUnit.MINUTES);
        Instant finalEnd = endTime.truncatedTo(ChronoUnit.MINUTES);
        int persistedM1 = 0;

        while (!cursor.isAfter(finalEnd)) {
            List<Candle> page = marketDataProvider.getExchangeCandles(symbol, Timeframe.M1, pageSize, cursor, finalEnd);
            if (page.isEmpty()) {
                break;
            }

            List<Candle> ordered = page.stream()
                    .filter(c -> !c.openTime().isAfter(finalEnd))
                    .sorted(Comparator.comparing(Candle::openTime))
                    .toList();
            if (ordered.isEmpty()) {
                break;
            }

            persistedM1 += candlePersistenceService.saveClosedCandles(ordered);

            Instant lastOpen = ordered.get(ordered.size() - 1).openTime();
            if (!lastOpen.isAfter(cursor)) {
                break;
            }

            cursor = lastOpen.plus(Timeframe.M1.getDuration());
            if (ordered.size() < pageSize) {
                break;
            }
            if (sleepMs > 0) {
                sleepQuietly(sleepMs);
            }
        }

        AggregationResult aggregated = aggregateAndPersistHigherTimeframes(symbol, startTime, endTime);
        boolean featuresReady = runFeatureEngineering(symbol);

        LOGGER.info("Binance paginated backfill completed for {} -> M1={}, M5={}, M15={}, H1={}, featuresReady={}",
                symbol, persistedM1, aggregated.m5(), aggregated.m15(), aggregated.h1(), featuresReady);

        return new SymbolBackfillResult(symbol, persistedM1, aggregated.m5(), aggregated.m15(), aggregated.h1(), featuresReady);
    }

    private AggregationResult aggregateAndPersistHigherTimeframes(String symbol, Instant startTime, Instant endTime) {
        Instant from = startTime.truncatedTo(ChronoUnit.MINUTES);
        Instant to = endTime.plus(Timeframe.M1.getDuration()).truncatedTo(ChronoUnit.MINUTES);

        List<OhlcvCandleEntity> m1Rows = ohlcvCandleRepository.findRange(symbol, "M1", from, to);
        if (m1Rows.isEmpty()) {
            return new AggregationResult(0, 0, 0);
        }

        List<Candle> m1Candles = m1Rows.stream()
                .map(c -> new Candle(c.getSymbol(), Timeframe.M1, c.getOpenTime(),
                        c.getOpenPrice(), c.getHighPrice(), c.getLowPrice(), c.getClosePrice(), c.getVolume()))
                .sorted(Comparator.comparing(Candle::openTime))
                .toList();

        Instant referenceTime = endTime.plus(Timeframe.H1.getDuration());
        int m5 = persistInChunks(aggregateClosedCandles(m1Candles, Timeframe.M5, referenceTime), 5000);
        int m15 = persistInChunks(aggregateClosedCandles(m1Candles, Timeframe.M15, referenceTime), 5000);
        int h1 = persistInChunks(aggregateClosedCandles(m1Candles, Timeframe.H1, referenceTime), 5000);
        return new AggregationResult(m5, m15, h1);
    }

    private int persistInChunks(List<Candle> candles, int chunkSize) {
        if (candles.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (int i = 0; i < candles.size(); i += chunkSize) {
            int end = Math.min(candles.size(), i + chunkSize);
            total += candlePersistenceService.saveClosedCandles(candles.subList(i, end));
        }
        return total;
    }

    private boolean runFeatureEngineering(String symbol) {
        Long instrumentId = featureEngineeringService.findInstrumentIdPublic(symbol);
        if (instrumentId == null) {
            LOGGER.warn("Skipping feature engineering for {}: instrument not found", symbol);
            return false;
        }

        boolean any = false;
        for (String tf : List.of("M5", "M15", "H1")) {
            try {
                any = featureEngineeringService.calculateAndPersistFeatures(
                        instrumentId,
                        symbol,
                        tf,
                        FEATURE_MIN_CANDLES
                ) || any;
            } catch (Exception ex) {
                LOGGER.error("Feature engineering failed during backfill for {} / {}: {}", symbol, tf, ex.getMessage(), ex);
            }
        }
        return any;
    }

    List<Candle> aggregateClosedCandles(List<Candle> m1Candles, Timeframe targetTimeframe, Instant referenceTime) {
        if (targetTimeframe == Timeframe.M1 || m1Candles.isEmpty()) {
            return List.of();
        }

        Map<Instant, List<Candle>> grouped = new LinkedHashMap<>();
        for (Candle candle : m1Candles.stream().sorted(Comparator.comparing(Candle::openTime)).toList()) {
            Instant bucketStart = bucketStart(candle.openTime(), targetTimeframe);
            grouped.computeIfAbsent(bucketStart, ignored -> new ArrayList<>()).add(candle);
        }

        List<Candle> aggregated = new ArrayList<>();
        for (Map.Entry<Instant, List<Candle>> entry : grouped.entrySet()) {
            Instant bucketStart = entry.getKey();
            List<Candle> bucketCandles = entry.getValue();

            if (!isWindowClosed(bucketStart, targetTimeframe, referenceTime)) {
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

        return new Candle(first.symbol(), targetTimeframe, bucketStart, first.open(), high, low, last.close(), volume);
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

    private boolean isWindowClosed(Instant bucketStart, Timeframe timeframe, Instant referenceTime) {
        return !bucketStart.plus(timeframe.getDuration()).isAfter(referenceTime.truncatedTo(ChronoUnit.MINUTES));
    }

    private Instant bucketStart(Instant openTime, Timeframe timeframe) {
        ZonedDateTime utc = openTime.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
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
        return unique.values().stream().sorted(Comparator.comparing(Candle::openTime)).toList();
    }

    private void sleepQuietly(long sleepMs) {
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private record AggregationResult(int m5, int m15, int h1) {
    }

    public record SymbolBackfillResult(
            String symbol,
            int persistedM1,
            int persistedM5,
            int persistedM15,
            int persistedH1,
            boolean featuresReady
    ) {
    }

    public record BackfillSummary(
            List<SymbolBackfillResult> symbols,
            int totalPersistedM1,
            int totalPersistedM5,
            int totalPersistedM15,
            int totalPersistedH1
    ) {
    }
}
