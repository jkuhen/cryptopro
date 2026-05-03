package com.kuhen.cryptopro.data;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Example scheduler demonstrating feature engineering module usage.
 *
 * <p>This component shows how to integrate the FeatureEngineeringService
 * into a real application with automatic scheduling for incremental updates.
 *
 * <p>Usage: Uncomment @Component annotation and add to your Spring Boot application.
 */
// @Component
class FeatureEngineeringExampleScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureEngineeringExampleScheduler.class);

    private final FeatureEngineeringService featureService;

    // Constants for feature calculation
    private static final int MIN_CANDLES_REQUIRED = 250;  // For EMA200 + safety margin
    private static final List<String> MONITORED_SYMBOLS = Arrays.asList(
            "BTCUSDT", "ETHUSDT", "SOLUSDT"
    );
    private static final List<String> MONITORED_TIMEFRAMES = Arrays.asList(
            "M5", "M15", "H1"
    );

    public FeatureEngineeringExampleScheduler(FeatureEngineeringService featureService) {
        this.featureService = featureService;
    }

    /**
     * Calculates features for all monitored symbols and timeframes.
     *
     * <p>Runs every minute to process the latest closed candle.
     * Safe to call frequently - only processes new data.
     */
    @Scheduled(fixedRate = 60_000)
    public void calculateFeaturesEveryMinute() {
        try {
            long startTime = System.currentTimeMillis();

            int count = featureService.calculateAndPersistFeaturesForAll(
                    MONITORED_SYMBOLS,
                    MONITORED_TIMEFRAMES,
                    MIN_CANDLES_REQUIRED
            );

            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("Feature engineering completed: {} features in {}ms", count, duration);

        } catch (Exception ex) {
            LOGGER.error("Feature engineering failed", ex);
        }
    }

    /**
     * Calculates features every 5 minutes (alternative less frequent schedule).
     */
    @Scheduled(fixedRate = 300_000)
    public void calculateFeaturesEvery5Minutes() {
        // Same logic, just less frequent
        calculateFeaturesEveryMinute();
    }

    /**
     * Example: Calculate features for a single symbol on-demand.
     *
     * @param symbol the symbol to process (e.g., "BTCUSDT")
     * @param timeframe the timeframe to process (e.g., "H1")
     * @return true if successful
     */
    public boolean calculateFeaturesForSymbol(String symbol, String timeframe) {
        try {
            // In a real app, look up the instrument ID from the database
            Long instrumentId = getInstrumentIdForSymbol(symbol);
            if (instrumentId == null) {
                LOGGER.warn("Unknown symbol: {}", symbol);
                return false;
            }

            boolean success = featureService.calculateAndPersistFeatures(
                    instrumentId,
                    symbol,
                    timeframe,
                    MIN_CANDLES_REQUIRED
            );

            if (success) {
                LOGGER.info("Features calculated for {} / {}", symbol, timeframe);

                // Retrieve and inspect the latest features
                inspectLatestFeatures(instrumentId);
            }

            return success;

        } catch (Exception ex) {
            LOGGER.error("Feature calculation failed for {} / {}", symbol, timeframe, ex);
            return false;
        }
    }

    /**
     * Example: Retrieve and inspect the latest features for a symbol.
     */
    private void inspectLatestFeatures(Long instrumentId) {
        try {
            List<com.kuhen.cryptopro.data.entity.FeaturesEntity> features =
                    featureService.getLatestFeatures(instrumentId, 1);

            if (!features.isEmpty()) {
                com.kuhen.cryptopro.data.entity.FeaturesEntity latest = features.get(0);
                LOGGER.info("Latest features for {}: EMA20={}, EMA50={}, RSI={}, ATR={}, Vol MA={}",
                        instrumentId,
                        String.format("%.2f", latest.getEma20()),
                        String.format("%.2f", latest.getEma50()),
                        String.format("%.2f", latest.getRsi()),
                        String.format("%.6f", latest.getAtr()),
                        String.format("%.0f", latest.getVolumeMa())
                );
            }
        } catch (Exception ex) {
            LOGGER.debug("Could not retrieve latest features", ex);
        }
    }

    /**
     * Example: Look up instrument ID from symbol.
     * In a real application, this would query the database or cache.
     */
    private Long getInstrumentIdForSymbol(String symbol) {
        // This is a placeholder - implement actual lookup logic
        return switch (symbol) {
            case "BTCUSDT" -> 1L;
            case "ETHUSDT" -> 2L;
            case "SOLUSDT" -> 3L;
            default -> null;
        };
    }
}

/**
 * Example: REST Controller for on-demand feature engineering.
 *
 * Usage:
 *   GET /api/features/calculate?symbol=BTCUSDT&timeframe=H1
 */
// @RestController
// @RequestMapping("/api/features")
class FeatureEngineeringExampleController {

    // private final FeatureEngineeringExampleScheduler scheduler;
    //
    // @GetMapping("/calculate")
    // public ResponseEntity<Map<String, Object>> calculateFeatures(
    //         @RequestParam String symbol,
    //         @RequestParam String timeframe) {
    //
    //     boolean success = scheduler.calculateFeaturesForSymbol(symbol, timeframe);
    //
    //     return ResponseEntity.ok(Map.of(
    //             "symbol", symbol,
    //             "timeframe", timeframe,
    //             "success", success,
    //             "timestamp", Instant.now()
    //     ));
    // }
}

/**
 * Example: Usage in a trading signal service.
 */
// @Service
class TradingSignalExampleService {

    // private final FeatureEngineeringService featureService;
    //
    // /**
    //  * Generate trading signals based on calculated features.
    //  */
    // public List<Signal> generateSignals(Long instrumentId) {
    //     List<com.kuhen.cryptopro.data.entity.FeaturesEntity> features =
    //             featureService.getLatestFeatures(instrumentId, 10);
    //
    //     List<Signal> signals = new ArrayList<>();
    //
    //     for (FeaturesEntity feature : features) {
    //         // Example signal logic
    //         if (feature.getRsi() < 30 && feature.getEma20() > feature.getEma50()) {
    //             signals.add(new BuySignal(instrumentId, feature.getRecordedAt()));
    //         }
    //         if (feature.getRsi() > 70 && feature.getEma20() < feature.getEma50()) {
    //             signals.add(new SellSignal(instrumentId, feature.getRecordedAt()));
    //         }
    //     }
    //
    //     return signals;
    // }
}



