package com.kuhen.cryptopro.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TradeAnalyticsRepository extends JpaRepository<TradeAnalyticsEntity, Long> {

    // Basic time-windowed fetches
    List<TradeAnalyticsEntity> findBySymbolAndClosedAtAfterOrderByClosedAtAsc(
            String symbol, Instant after);

    List<TradeAnalyticsEntity> findBySymbolAndClosedAtBetweenOrderByClosedAtAsc(
            String symbol, Instant from, Instant to);

    List<TradeAnalyticsEntity> findAllByOrderByClosedAtAsc();

    // Filtering by regime / conditions
    @Query("SELECT t FROM TradeAnalyticsEntity t WHERE t.symbol = :symbol AND t.regime = :regime ORDER BY t.closedAt ASC")
    List<TradeAnalyticsEntity> findBySymbolAndRegime(
            @Param("symbol") String symbol, @Param("regime") String regime);

    @Query("SELECT t FROM TradeAnalyticsEntity t WHERE t.symbol = :symbol AND t.liquiditySweep = true ORDER BY t.closedAt ASC")
    List<TradeAnalyticsEntity> findBySymbolWithLiquiditySweep(@Param("symbol") String symbol);

    @Query("SELECT t FROM TradeAnalyticsEntity t WHERE t.symbol = :symbol AND t.volumeSpike = true ORDER BY t.closedAt ASC")
    List<TradeAnalyticsEntity> findBySymbolWithVolumeSpike(@Param("symbol") String symbol);

    @Query("SELECT t FROM TradeAnalyticsEntity t WHERE t.symbol = :symbol AND t.oiConfirmation = true ORDER BY t.closedAt ASC")
    List<TradeAnalyticsEntity> findBySymbolWithOiConfirmation(@Param("symbol") String symbol);

    @Query("SELECT t FROM TradeAnalyticsEntity t WHERE t.symbol = :symbol AND t.closeReason = :reason ORDER BY t.closedAt ASC")
    List<TradeAnalyticsEntity> findBySymbolAndCloseReason(
            @Param("symbol") String symbol, @Param("reason") String reason);

    // Aggregate queries
    @Query("SELECT COUNT(t) FROM TradeAnalyticsEntity t WHERE t.symbol = :symbol")
    long countBySymbol(@Param("symbol") String symbol);

    @Query("SELECT COALESCE(SUM(t.pnl), 0) FROM TradeAnalyticsEntity t WHERE t.symbol = :symbol AND t.pnl > 0")
    double sumGrossProfitBySymbol(@Param("symbol") String symbol);

    @Query("SELECT COALESCE(SUM(t.pnl), 0) FROM TradeAnalyticsEntity t WHERE t.symbol = :symbol AND t.pnl < 0")
    double sumGrossLossBySymbol(@Param("symbol") String symbol);

    @Query("SELECT DISTINCT t.symbol FROM TradeAnalyticsEntity t ORDER BY t.symbol ASC")
    List<String> findDistinctSymbols();
}

