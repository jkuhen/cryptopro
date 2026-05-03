package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.Timeframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports historical OHLCV CSV files from disk and backfills candles/features.
 */
@Service
public class CsvHistoricalBackfillService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CsvHistoricalBackfillService.class);
    private static final int DEFAULT_MIN_CANDLES = 220;

    private final CandlePersistenceService candlePersistenceService;
    private final FeatureEngineeringService featureEngineeringService;

    public CsvHistoricalBackfillService(
            CandlePersistenceService candlePersistenceService,
            FeatureEngineeringService featureEngineeringService) {
        this.candlePersistenceService = candlePersistenceService;
        this.featureEngineeringService = featureEngineeringService;
    }

    public BackfillSummary run(Path dataDir, String pattern, Timeframe timeframe) {
        if (dataDir == null || !Files.isDirectory(dataDir)) {
            throw new IllegalArgumentException("Backfill data directory does not exist: " + dataDir);
        }

        List<BackfillFileResult> results = new ArrayList<>();
        int totalPersisted = 0;

        try (var files = Files.newDirectoryStream(dataDir, pattern)) {
            for (Path file : files) {
                BackfillFileResult result = importSingleCsv(file, timeframe);
                results.add(result);
                totalPersisted += result.persistedCandles();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read data directory: " + dataDir, ex);
        }

        return new BackfillSummary(results, totalPersisted);
    }

    private BackfillFileResult importSingleCsv(Path file, Timeframe timeframe) {
        List<Candle> parsed = parseCandles(file, timeframe);
        if (parsed.isEmpty()) {
            LOGGER.warn("CSV {} has no valid rows; skipping", file.getFileName());
            return new BackfillFileResult(file.getFileName().toString(), null, 0, false);
        }

        String symbol = parsed.get(0).symbol().toUpperCase();
        int persisted = candlePersistenceService.saveClosedCandles(parsed);

        boolean featuresPersisted = false;
        Long instrumentId = featureEngineeringService.findInstrumentIdPublic(symbol);
        if (instrumentId == null) {
            LOGGER.warn("No instrument found for symbol {} while backfilling {}", symbol, file.getFileName());
        } else {
            featuresPersisted = featureEngineeringService.calculateAndPersistFeatures(
                    instrumentId,
                    symbol,
                    timeframe.name(),
                    DEFAULT_MIN_CANDLES
            );
        }

        LOGGER.info(
                "CSV backfill completed for {} => symbol={}, timeframe={}, candlesPersisted={}, latestFeaturesPersisted={}",
                file.getFileName(),
                symbol,
                timeframe,
                persisted,
                featuresPersisted
        );

        return new BackfillFileResult(file.getFileName().toString(), symbol, persisted, featuresPersisted);
    }

    private List<Candle> parseCandles(Path file, Timeframe timeframe) {
        List<Candle> rows = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                if (line.startsWith("https://") || line.startsWith("Unix,")) {
                    continue;
                }

                String[] cols = line.split(",");
                if (cols.length < 8) {
                    continue;
                }

                try {
                    long ts = Long.parseLong(cols[0].trim());
                    String symbol = cols[2].trim().toUpperCase();
                    double open = Double.parseDouble(cols[3].trim());
                    double high = Double.parseDouble(cols[4].trim());
                    double low = Double.parseDouble(cols[5].trim());
                    double close = Double.parseDouble(cols[6].trim());
                    double volume = Double.parseDouble(cols[7].trim());

                    rows.add(new Candle(
                            symbol,
                            timeframe,
                            Instant.ofEpochMilli(ts),
                            open,
                            high,
                            low,
                            close,
                            volume
                    ));
                } catch (Exception ignored) {
                    // Keep import resilient to malformed rows.
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse csv file: " + file, ex);
        }

        rows.sort(Comparator.comparing(Candle::openTime));

        // Keep the last value when a CSV contains duplicate timestamps.
        Map<String, Candle> unique = new LinkedHashMap<>();
        for (Candle candle : rows) {
            String key = candle.symbol() + '|' + candle.timeframe().name() + '|' + candle.openTime();
            unique.put(key, candle);
        }
        return new ArrayList<>(unique.values());
    }

    public record BackfillFileResult(
            String fileName,
            String symbol,
            int persistedCandles,
            boolean latestFeaturesPersisted
    ) {
    }

    public record BackfillSummary(
            List<BackfillFileResult> files,
            int totalPersistedCandles
    ) {
    }
}

