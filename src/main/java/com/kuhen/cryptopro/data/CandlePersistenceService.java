package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.repository.OhlcvCandleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persists closed OHLCV candles to the {@code ohlcv_candle} table.
 *
 * <p>Each save runs in a dedicated thread pool ({@code candlePersistenceExecutor})
 * so the Binance WebSocket callback thread is never blocked by database I/O.
 *
 * <p>An upsert strategy (INSERT … ON CONFLICT DO UPDATE) ensures the table
 * remains consistent even if a reconnect causes the same candle to arrive twice.
 */
@Service
public class CandlePersistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CandlePersistenceService.class);

    private final OhlcvCandleRepository repository;

    /** Default constructor for framework instantiation. */
    public CandlePersistenceService() {
        this.repository = null;
    }

    @Autowired
    public CandlePersistenceService(OhlcvCandleRepository repository) {
        this.repository = repository;
    }

    /**
     * Persists a single closed candle asynchronously.
     *
     * <p>Failures are logged but do not propagate so they cannot disrupt the
     * WebSocket feed.
     *
     * @param candle the fully closed candle received from the WebSocket stream
     */
    @Async("candlePersistenceExecutor")
    @Transactional
    public void saveClosedCandle(Candle candle) {
        try {
            upsert(candle);
            LOGGER.debug("Persisted closed candle {}/{} open_time={}",
                    candle.symbol(), candle.timeframe(), candle.openTime());
        } catch (Exception ex) {
            LOGGER.error("Failed to persist closed candle {}/{} open_time={}: {}",
                    candle.symbol(), candle.timeframe(), candle.openTime(), ex.getMessage(), ex);
        }
    }

    /**
     * Persists a batch of closed candles synchronously.
     *
     * <p>This path is used by the scheduled sync / aggregation job so the caller knows
     * exactly how many candles were attempted in the current cycle.
     *
     * @return the number of candles submitted to the upsert path
     */
    @Transactional
    public int saveClosedCandles(List<Candle> candles) {
        int persisted = 0;
        for (Candle candle : candles) {
            upsert(candle);
            persisted++;
        }
        return persisted;
    }

    private void upsert(Candle candle) {
        repository.upsert(
                candle.symbol(),
                candle.timeframe().name(),
                candle.openTime(),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume()
        );
    }
}

