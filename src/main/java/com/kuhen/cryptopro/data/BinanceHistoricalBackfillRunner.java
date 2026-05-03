package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.config.BinanceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One-shot runner that backfills historical Binance M1 candles using startTime pagination.
 */
@Component
@ConditionalOnExpression("'${cryptopro.data.provider:}'=='binance' and '${cryptopro.backfill.binance.enabled:false}'=='true'")
public class BinanceHistoricalBackfillRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BinanceHistoricalBackfillRunner.class);

    private final BinanceHistoricalBackfillService backfillService;
    private final BinanceProperties binanceProperties;
    private final Environment environment;
    private final ConfigurableApplicationContext applicationContext;

    public BinanceHistoricalBackfillRunner(
            BinanceHistoricalBackfillService backfillService,
            BinanceProperties binanceProperties,
            Environment environment,
            ConfigurableApplicationContext applicationContext
    ) {
        this.backfillService = backfillService;
        this.binanceProperties = binanceProperties;
        this.environment = environment;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) {
        List<String> symbols = resolveSymbols();
        Instant endTime = resolveInstant("cryptopro.backfill.binance.end-time", Instant.now().truncatedTo(ChronoUnit.MINUTES));
        Instant startTime = resolveInstant("cryptopro.backfill.binance.start-time", endTime.minus(30, ChronoUnit.DAYS));

        int pageSize = Math.max(10, Math.min(1000,
                Integer.parseInt(environment.getProperty("cryptopro.backfill.binance.page-size", "1000"))));
        long sleepMs = Math.max(0L,
                Long.parseLong(environment.getProperty("cryptopro.backfill.binance.sleep-ms", "200")));
        boolean exitOnComplete = Boolean.parseBoolean(
                environment.getProperty("cryptopro.backfill.binance.exit-on-complete", "true"));

        LOGGER.info("Starting Binance paginated backfill: symbols={}, start={}, end={}, pageSize={}",
                symbols, startTime, endTime, pageSize);

        BinanceHistoricalBackfillService.BackfillSummary summary = backfillService.backfill(
                symbols,
                startTime,
                endTime,
                pageSize,
                sleepMs
        );

        LOGGER.info("Binance backfill finished: symbolsProcessed={}, M1={}, M5={}, M15={}, H1={}",
                summary.symbols().size(),
                summary.totalPersistedM1(),
                summary.totalPersistedM5(),
                summary.totalPersistedM15(),
                summary.totalPersistedH1());

        if (exitOnComplete) {
            int code = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(code);
        }
    }

    private List<String> resolveSymbols() {
        String configured = environment.getProperty("cryptopro.backfill.binance.symbols", "");
        if (configured != null && !configured.isBlank()) {
            return Arrays.stream(configured.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(String::toUpperCase)
                    .distinct()
                    .collect(Collectors.toList());
        }

        if (binanceProperties.getWebsocketSymbols() != null && !binanceProperties.getWebsocketSymbols().isEmpty()) {
            return binanceProperties.getWebsocketSymbols().stream()
                    .map(String::toUpperCase)
                    .distinct()
                    .collect(Collectors.toList());
        }

        return List.of(binanceProperties.getDefaultSymbol().toUpperCase());
    }

    private Instant resolveInstant(String key, Instant fallback) {
        String raw = environment.getProperty(key, "");
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Instant.parse(raw.trim());
    }
}
