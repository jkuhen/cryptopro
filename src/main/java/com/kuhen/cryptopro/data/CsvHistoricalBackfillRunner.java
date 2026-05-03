package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.data.model.Timeframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * One-shot backfill runner for importing historical CSVs into ohlcv_candle.
 */
@Component
@ConditionalOnProperty(prefix = "cryptopro.backfill.csv", name = "enabled", havingValue = "true")
public class CsvHistoricalBackfillRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CsvHistoricalBackfillRunner.class);

    private final CsvHistoricalBackfillService backfillService;
    private final Environment environment;
    private final ConfigurableApplicationContext applicationContext;

    public CsvHistoricalBackfillRunner(
            CsvHistoricalBackfillService backfillService,
            Environment environment,
            ConfigurableApplicationContext applicationContext) {
        this.backfillService = backfillService;
        this.environment = environment;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) {
        String dataDir = environment.getProperty("cryptopro.backfill.csv.data-dir", "data");
        String pattern = environment.getProperty("cryptopro.backfill.csv.file-pattern", "Binance_*USDT_d.csv");
        String timeframeRaw = environment.getProperty("cryptopro.backfill.csv.target-timeframe", "H1");
        boolean exitOnComplete = Boolean.parseBoolean(
                environment.getProperty("cryptopro.backfill.csv.exit-on-complete", "true")
        );

        Timeframe timeframe;
        try {
            timeframe = Timeframe.valueOf(timeframeRaw.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid cryptopro.backfill.csv.target-timeframe: " + timeframeRaw, ex);
        }

        LOGGER.info("Starting CSV historical backfill: dir={}, pattern={}, timeframe={}", dataDir, pattern, timeframe);
        CsvHistoricalBackfillService.BackfillSummary summary = backfillService.run(Path.of(dataDir), pattern, timeframe);
        LOGGER.info("CSV historical backfill finished: filesProcessed={}, candlesPersisted={}",
                summary.files().size(), summary.totalPersistedCandles());

        if (exitOnComplete) {
            LOGGER.info("CSV backfill run completed. Exiting process by configuration.");
            int code = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(code);
        }
    }
}

