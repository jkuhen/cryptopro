package com.kuhen.cryptopro.analytics;

import com.kuhen.cryptopro.ai.MarketRegime;
import com.kuhen.cryptopro.strategy.SignalDirection;
import com.kuhen.cryptopro.trade.ClosedTrade;
import com.kuhen.cryptopro.trade.TradeCloseReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {

    private AnalyticsService service;
    private TradeAnalyticsRepository analyticsRepo;
    private StrategyPerformanceSnapshotRepository snapshotRepo;

    @BeforeEach
    void setUp() {
        analyticsRepo = mock(TradeAnalyticsRepository.class);
        snapshotRepo  = mock(StrategyPerformanceSnapshotRepository.class);

        when(analyticsRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(snapshotRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(snapshotRepo.findAllBySymbol(anyString())).thenReturn(List.of());

        service = new AnalyticsService(analyticsRepo, snapshotRepo);
    }

    // -----------------------------------------------------------------------
    // Record
    // -----------------------------------------------------------------------

    @Test
    void recordPersistsTradeAnalyticsEntity() {
        TradeAnalyticsRecord rec = fullRecord(closedTrade("t1", "BTCUSDT", 1.0, 65000, 67000, TradeCloseReason.TAKE_PROFIT_HIT));
        service.record(rec);
        verify(analyticsRepo).save(any(TradeAnalyticsEntity.class));
    }

    @Test
    void recordedEntityHasCorrectFields() {
        ClosedTrade trade = closedTrade("t2", "ETHUSDT", 2.0, 3000, 3200, TradeCloseReason.MANUAL);
        TradeAnalyticsRecord rec = TradeAnalyticsRecord.of(trade, MarketRegime.TRENDING, true, false, true);

        TradeAnalyticsEntity saved = service.record(rec);

        assertEquals("t2",      saved.getTradeUuid());
        assertEquals("ETHUSDT", saved.getSymbol());
        assertEquals("LONG",    saved.getDirection());
        assertEquals("MANUAL",  saved.getCloseReason());
        assertEquals("TRENDING",saved.getRegime());
        assertTrue(saved.isLiquiditySweep());
        assertFalse(saved.isVolumeSpike());
        assertTrue(saved.isOiConfirmation());
        assertEquals(400.0, saved.getPnl(), 0.01);  // (3200-3000)*2
    }

    // -----------------------------------------------------------------------
    // Win rate
    // -----------------------------------------------------------------------

    @Test
    void winRateIsCorrect() {
        when(analyticsRepo.findBySymbolAndClosedAtAfterOrderByClosedAtAsc(eq("BTCUSDT"), any()))
                .thenReturn(mixedTrades("BTCUSDT", 3, 1)); // 3 wins, 1 loss

        AnalyticsSummary summary = service.computeSummary("BTCUSDT", null, null);

        assertEquals(4, summary.totalTrades());
        assertEquals(3, summary.wins());
        assertEquals(1, summary.losses());
        assertEquals(75.0, summary.winRatePercent(), 0.01);
    }

    @Test
    void winRateIsZeroWithNoTrades() {
        when(analyticsRepo.findBySymbolAndClosedAtAfterOrderByClosedAtAsc(eq("BTCUSDT"), any()))
                .thenReturn(List.of());

        AnalyticsSummary summary = service.computeSummary("BTCUSDT", null, null);

        assertEquals(0, summary.totalTrades());
        assertEquals(0.0, summary.winRatePercent());
    }

    // -----------------------------------------------------------------------
    // Profit factor
    // -----------------------------------------------------------------------

    @Test
    void profitFactorIsGrossProfitOverGrossLoss() {
        // 2 wins of +200 each, 1 loss of -100
        when(analyticsRepo.findBySymbolAndClosedAtAfterOrderByClosedAtAsc(eq("BTCUSDT"), any()))
                .thenReturn(List.of(
                        entity("BTCUSDT", 200, "LONG"),
                        entity("BTCUSDT", 200, "LONG"),
                        entity("BTCUSDT", -100, "LONG")
                ));

        AnalyticsSummary summary = service.computeSummary("BTCUSDT", null, null);

        assertEquals(4.0, summary.profitFactor(), 0.01);  // 400 / 100
        assertEquals(300.0, summary.totalPnl(), 0.01);
    }

    @Test
    void profitFactorIsZeroWithNoTrades() {
        when(analyticsRepo.findBySymbolAndClosedAtAfterOrderByClosedAtAsc(eq("BTCUSDT"), any()))
                .thenReturn(List.of());

        assertEquals(0.0, service.computeSummary("BTCUSDT", null, null).profitFactor());
    }

    // -----------------------------------------------------------------------
    // Drawdown
    // -----------------------------------------------------------------------

    @Test
    void maxDrawdownIsComputedFromEquityCurve() {
        // equity: 100 → 200 → 50 → 250
        // peak=200, trough=50, dd=150
        when(analyticsRepo.findBySymbolAndClosedAtAfterOrderByClosedAtAsc(eq("BTCUSDT"), any()))
                .thenReturn(List.of(
                        entity("BTCUSDT",  100, "LONG"),
                        entity("BTCUSDT",  100, "LONG"),
                        entity("BTCUSDT", -150, "LONG"),
                        entity("BTCUSDT",  200, "LONG")
                ));

        AnalyticsSummary summary = service.computeSummary("BTCUSDT", null, null);

        assertEquals(150.0, summary.maxDrawdown(), 0.01);
        assertTrue(summary.maxDrawdownPercent() > 0.0);
    }

    @Test
    void drawdownIsZeroForAlwaysProfitableCurve() {
        when(analyticsRepo.findBySymbolAndClosedAtAfterOrderByClosedAtAsc(eq("BTCUSDT"), any()))
                .thenReturn(List.of(
                        entity("BTCUSDT", 50, "LONG"),
                        entity("BTCUSDT", 75, "LONG"),
                        entity("BTCUSDT", 25, "LONG")
                ));

        assertEquals(0.0, service.computeSummary("BTCUSDT", null, null).maxDrawdown(), 0.01);
    }

    // -----------------------------------------------------------------------
    // Performance by condition / regime
    // -----------------------------------------------------------------------

    @Test
    void byRegimeBreakdownGroupsByRegimeField() {
        TradeAnalyticsEntity trending = entity("BTCUSDT", 200, "LONG");
        trending.setRegime("TRENDING");
        TradeAnalyticsEntity ranging  = entity("BTCUSDT", -50, "LONG");
        ranging.setRegime("RANGING");
        TradeAnalyticsEntity trending2 = entity("BTCUSDT", 100, "LONG");
        trending2.setRegime("TRENDING");

        when(analyticsRepo.findBySymbolAndClosedAtAfterOrderByClosedAtAsc(eq("BTCUSDT"), any()))
                .thenReturn(List.of(trending, ranging, trending2));

        List<AnalyticsBreakdown> regime = service.computeByRegime("BTCUSDT", null);

        AnalyticsBreakdown trendingBd = regime.stream().filter(b -> "TRENDING".equals(b.label())).findFirst().orElseThrow();
        assertEquals(2, trendingBd.totalTrades());
        assertEquals(100.0, trendingBd.winRatePercent(), 0.01);
        assertEquals(300.0, trendingBd.totalPnl(), 0.01);
    }

    @Test
    void byConditionBreakdownSplitsOnLiquiditySweep() {
        TradeAnalyticsEntity withSweep    = entity("BTCUSDT", 150, "LONG"); withSweep.setLiquiditySweep(true);
        TradeAnalyticsEntity withoutSweep = entity("BTCUSDT", -40, "LONG");

        when(analyticsRepo.findBySymbolAndClosedAtAfterOrderByClosedAtAsc(eq("BTCUSDT"), any()))
                .thenReturn(List.of(withSweep, withoutSweep));

        List<AnalyticsBreakdown> conditions = service.computeByCondition("BTCUSDT", null);

        AnalyticsBreakdown sweepTrue = conditions.stream()
                .filter(b -> "liquiditySweep_true".equals(b.label())).findFirst().orElseThrow();
        assertEquals(1, sweepTrue.totalTrades());
        assertEquals(150.0, sweepTrue.totalPnl(), 0.01);

        AnalyticsBreakdown sweepFalse = conditions.stream()
                .filter(b -> "liquiditySweep_false".equals(b.label())).findFirst().orElseThrow();
        assertEquals(1, sweepFalse.totalTrades());
        assertEquals(-40.0, sweepFalse.totalPnl(), 0.01);
    }

    // -----------------------------------------------------------------------
    // Equity curve
    // -----------------------------------------------------------------------

    @Test
    void equityCurveIsCumulativeSum() {
        when(analyticsRepo.findBySymbolAndClosedAtAfterOrderByClosedAtAsc(eq("BTCUSDT"), any()))
                .thenReturn(List.of(
                        entity("BTCUSDT", 100, "LONG"),
                        entity("BTCUSDT", -50, "LONG"),
                        entity("BTCUSDT", 200, "LONG")
                ));

        List<Double> curve = service.getEquityCurve("BTCUSDT", 0);

        assertEquals(3, curve.size());
        assertEquals(100.0, curve.get(0), 0.01);
        assertEquals(50.0,  curve.get(1), 0.01);
        assertEquals(250.0, curve.get(2), 0.01);
    }

    @Test
    void equityCurveLimitIsRespected() {
        when(analyticsRepo.findBySymbolAndClosedAtAfterOrderByClosedAtAsc(eq("BTCUSDT"), any()))
                .thenReturn(List.of(
                        entity("BTCUSDT", 10, "LONG"),
                        entity("BTCUSDT", 20, "LONG"),
                        entity("BTCUSDT", 30, "LONG"),
                        entity("BTCUSDT", 40, "LONG")
                ));

        List<Double> curve = service.getEquityCurve("BTCUSDT", 2);
        assertEquals(2, curve.size());
    }

    // -----------------------------------------------------------------------
    // Snapshot persistence
    // -----------------------------------------------------------------------

    @Test
    void computeAndPersistSnapshotSavesEntity() {
        when(analyticsRepo.findBySymbolAndClosedAtBetweenOrderByClosedAtAsc(any(), any(), any()))
                .thenReturn(List.of(entity("BTCUSDT", 100, "LONG")));

        service.computeAndPersistSnapshot("BTCUSDT", "DAILY", Instant.now().minusSeconds(86400), Instant.now());

        verify(snapshotRepo).save(any(StrategyPerformanceSnapshotEntity.class));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private TradeAnalyticsRecord fullRecord(ClosedTrade trade) {
        return TradeAnalyticsRecord.of(trade, MarketRegime.TRENDING, true, true, false);
    }

    private ClosedTrade closedTrade(String id, String symbol, double qty, double entry, double close, TradeCloseReason reason) {
        double pnl        = (close - entry) * qty;
        double pnlPercent = (pnl / (entry * qty)) * 100.0;
        return new ClosedTrade(id, symbol, SignalDirection.LONG, qty, entry, close,
                pnl, pnlPercent, reason, Instant.now().minusSeconds(60), Instant.now());
    }

    private List<TradeAnalyticsEntity> mixedTrades(String symbol, int wins, int losses) {
        List<TradeAnalyticsEntity> list = new ArrayList<>();
        for (int i = 0; i < wins;   i++) list.add(entity(symbol,   100, "LONG"));
        for (int i = 0; i < losses; i++) list.add(entity(symbol,  -100, "LONG"));
        return list;
    }

    private TradeAnalyticsEntity entity(String symbol, double pnl, String direction) {
        TradeAnalyticsEntity e = new TradeAnalyticsEntity();
        e.setSymbol(symbol);
        e.setDirection(direction);
        e.setPnl(pnl);
        e.setPnlPercent(pnl / 1000.0 * 100.0);
        e.setQuantity(1.0);
        e.setEntryPrice(65000.0);
        e.setClosePrice(pnl > 0 ? 65100.0 : 64900.0);
        e.setCloseReason("MANUAL");
        e.setOpenedAt(Instant.now().minusSeconds(300));
        e.setClosedAt(Instant.now());
        e.setHoldDurationSec(300);
        return e;
    }
}

