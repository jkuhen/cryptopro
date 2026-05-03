package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.data.dto.BinanceMarketSyncResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled runner for the Binance market-data sync service.
 *
 * <p>Runs every minute by default and skips overlapping executions.
 */
@Service
@ConditionalOnProperty(prefix = "cryptopro.binance", name = "sync-enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(BinanceMarketDataSyncService.class)
public class BinanceMarketDataScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BinanceMarketDataScheduler.class);

    private final BinanceMarketDataSyncService marketDataSyncService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BinanceMarketDataScheduler(BinanceMarketDataSyncService marketDataSyncService) {
        this.marketDataSyncService = marketDataSyncService;
    }

    @Scheduled(cron = "${cryptopro.binance.sync-cron:0 * * * * *}")
    public void syncCandles() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.warn("Skipping Binance scheduled sync because the previous run is still in progress.");
            return;
        }

        try {
            List<BinanceMarketSyncResultDto> results = marketDataSyncService.syncConfiguredSymbols();
            if (!results.isEmpty()) {
                LOGGER.info("Binance scheduled sync completed: {}", results);
            }
        } catch (Exception ex) {
            LOGGER.error("Scheduled Binance market-data sync failed: {}", ex.getMessage(), ex);
        } finally {
            running.set(false);
        }
    }
}

