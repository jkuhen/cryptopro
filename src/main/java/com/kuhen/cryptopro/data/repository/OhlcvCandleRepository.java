package com.kuhen.cryptopro.data.repository;

import com.kuhen.cryptopro.data.entity.OhlcvCandleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OhlcvCandleRepository extends JpaRepository<OhlcvCandleEntity, Long> {

    /**
     * Insert or update a candle row.
     *
     * <p>The conflict target is the natural key {@code (symbol, timeframe, open_time)}.
     * On conflict all price and volume fields are refreshed so late corrected klines
     * are stored with the final values.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO ohlcv_candle
                (symbol, timeframe, open_time, open_price, high_price, low_price, close_price, volume, created_at)
            VALUES
                (:symbol, :timeframe, :openTime, :openPrice, :highPrice, :lowPrice, :closePrice, :volume, NOW())
            ON CONFLICT (symbol, timeframe, open_time) DO UPDATE SET
                open_price  = EXCLUDED.open_price,
                high_price  = EXCLUDED.high_price,
                low_price   = EXCLUDED.low_price,
                close_price = EXCLUDED.close_price,
                volume      = EXCLUDED.volume
            """)
    void upsert(
            @Param("symbol")     String symbol,
            @Param("timeframe")  String timeframe,
            @Param("openTime")   Instant openTime,
            @Param("openPrice")  double openPrice,
            @Param("highPrice")  double highPrice,
            @Param("lowPrice")   double lowPrice,
            @Param("closePrice") double closePrice,
            @Param("volume")     double volume
    );

    /**
     * Returns the last {@code limit} closed candles for a symbol/timeframe,
     * ordered from oldest to newest.
     */
    @Query("""
            SELECT c FROM OhlcvCandleEntity c
            WHERE c.symbol = :symbol AND c.timeframe = :timeframe
            ORDER BY c.openTime DESC
            LIMIT :limit
            """)
    List<OhlcvCandleEntity> findLatest(
            @Param("symbol")    String symbol,
            @Param("timeframe") String timeframe,
            @Param("limit")     int limit
    );

    /** Returns candles for a symbol/timeframe in ascending open-time order. */
    @Query("""
            SELECT c FROM OhlcvCandleEntity c
            WHERE c.symbol = :symbol
              AND c.timeframe = :timeframe
              AND c.openTime >= :fromTime
              AND c.openTime < :toTime
            ORDER BY c.openTime ASC
            """)
    List<OhlcvCandleEntity> findRange(
            @Param("symbol")    String symbol,
            @Param("timeframe") String timeframe,
            @Param("fromTime")  Instant fromTime,
            @Param("toTime")    Instant toTime
    );

    /** Counts stored candles for a given symbol and timeframe. */
    long countBySymbolAndTimeframe(String symbol, String timeframe);
}

