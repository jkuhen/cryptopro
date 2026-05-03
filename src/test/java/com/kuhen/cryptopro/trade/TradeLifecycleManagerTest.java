package com.kuhen.cryptopro.trade;

import com.kuhen.cryptopro.config.TradeLifecycleProperties;
import com.kuhen.cryptopro.data.repository.TradeRepository;
import com.kuhen.cryptopro.execution.ExecutionResult;
import com.kuhen.cryptopro.execution.ExecutionStatus;
import com.kuhen.cryptopro.risk.RiskStateService;
import com.kuhen.cryptopro.strategy.SignalDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TradeLifecycleManagerTest {

    private TradeLifecycleManager manager;
    private RiskStateService riskState;
    private TradeRepository tradeRepository;

    @BeforeEach
    void setUp() {
        TradeLifecycleProperties props = new TradeLifecycleProperties();
        props.setTrailingStopAtrMultiplier(2.0);
        props.setTakeProfitAtrMultiplier(3.0);
        props.setCloseOnSignalReversal(true);

        tradeRepository = mock(TradeRepository.class);
        when(tradeRepository.save(any())).thenAnswer(inv -> {
            // Return entity with a synthetic DB id
            var entity = inv.getArgument(0, com.kuhen.cryptopro.data.entity.TradeEntity.class);
            try {
                var idField = com.kuhen.cryptopro.data.entity.TradeEntity.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(entity, 1L);
            } catch (Exception ignored) {}
            return entity;
        });
        when(tradeRepository.findById(1L)).thenReturn(Optional.of(new com.kuhen.cryptopro.data.entity.TradeEntity()));

        riskState = mock(RiskStateService.class);
        manager = new TradeLifecycleManager(props, tradeRepository, riskState);
    }

    // -----------------------------------------------------------------------
    // Open
    // -----------------------------------------------------------------------

    @Test
    void openTradeRegistersAndReturnsOpenTrade() {
        OpenTrade trade = manager.openTrade("BTCUSDT", SignalDirection.LONG, filled(1.0, 65000.0), 63000.0, 500.0);

        assertNotNull(trade);
        assertEquals("BTCUSDT", trade.getSymbol());
        assertEquals(SignalDirection.LONG, trade.getDirection());
        assertEquals(65000.0, trade.getEntryPrice());
        assertEquals(63000.0, trade.getInitialStopLoss());
        // TP = entry + atr * 3.0 = 65000 + 1500 = 66500
        assertEquals(66500.0, trade.getTakeProfitPrice());
        assertEquals(1, manager.openTradeCount());
    }

    @Test
    void openTradeReturnsNullForRejectedExecution() {
        ExecutionResult rejected = new ExecutionResult(
                ExecutionStatus.REJECTED, 1.0, 0.0, 0.0, 0.0, 0.0, 1, List.of(), "No liquidity");
        OpenTrade trade = manager.openTrade("BTCUSDT", SignalDirection.LONG, rejected, 63000.0, 500.0);
        assertNull(trade);
        assertEquals(0, manager.openTradeCount());
    }

    // -----------------------------------------------------------------------
    // Trailing stop update
    // -----------------------------------------------------------------------

    @Test
    void trailingStopRatchetsUpForLongAsAssetRises() {
        OpenTrade trade = manager.openTrade("BTCUSDT", SignalDirection.LONG, filled(1.0, 65000.0), 63000.0, 500.0);
        double initialStop = trade.getTrailingStopLoss();

        // Price rises: trailing stop should ratchet up
        manager.onPriceTick("BTCUSDT", 67000.0, 500.0);

        assertTrue(trade.getTrailingStopLoss() > initialStop,
                "Trailing stop should have ratcheted up with price rise");
        // new extreme = 67000, distance = 500*2=1000, new stop = 66000
        assertEquals(66000.0, trade.getTrailingStopLoss(), 0.001);
    }

    @Test
    void trailingStopNeverWidensForLong() {
        OpenTrade trade = manager.openTrade("BTCUSDT", SignalDirection.LONG, filled(1.0, 65000.0), 63000.0, 500.0);
        manager.onPriceTick("BTCUSDT", 67000.0, 500.0); // ratchet up
        double stopAfterRise = trade.getTrailingStopLoss();

        manager.onPriceTick("BTCUSDT", 64000.0, 500.0); // price drops

        assertEquals(stopAfterRise, trade.getTrailingStopLoss(), 0.001,
                "Trailing stop must not widen when price falls");
    }

    @Test
    void trailingStopRatchetsDownForShortAsAssetFalls() {
        OpenTrade trade = manager.openTrade("BTCUSDT", SignalDirection.SHORT, filled(1.0, 65000.0), 67000.0, 500.0);
        double initialStop = trade.getTrailingStopLoss();

        manager.onPriceTick("BTCUSDT", 63000.0, 500.0); // price falls

        assertTrue(trade.getTrailingStopLoss() < initialStop,
                "Short trailing stop should ratchet down as price falls");
        // extreme = 63000, distance = 1000, new stop = 64000
        assertEquals(64000.0, trade.getTrailingStopLoss(), 0.001);
    }

    // -----------------------------------------------------------------------
    // Auto-close on stop loss hit
    // -----------------------------------------------------------------------

    @Test
    void autoClosesLongTradeWhenPriceCrossesTrailingStop() {
        OpenTrade trade = manager.openTrade("BTCUSDT", SignalDirection.LONG, filled(1.0, 65000.0), 63000.0, 500.0);

        // Simulate price falling below initial stop (63000 is initial trailing stop)
        List<ClosedTrade> closedList = manager.onPriceTick("BTCUSDT", 62500.0, 500.0);

        assertEquals(1, closedList.size());
        ClosedTrade closed = closedList.get(0);
        assertEquals(trade.getTradeId(), closed.tradeId());
        assertEquals(TradeCloseReason.STOP_LOSS_HIT, closed.reason());
        assertEquals(0, manager.openTradeCount());
        verify(riskState).recordTradeClosed(any(ClosedTrade.class));
    }

    @Test
    void autoClosesShortTradeWhenPriceCrossesTrailingStop() {
        OpenTrade trade = manager.openTrade("BTCUSDT", SignalDirection.SHORT, filled(1.0, 65000.0), 67000.0, 500.0);

        List<ClosedTrade> closedList = manager.onPriceTick("BTCUSDT", 67500.0, 500.0);

        assertEquals(1, closedList.size());
        assertEquals(TradeCloseReason.STOP_LOSS_HIT, closedList.get(0).reason());
        assertEquals(0, manager.openTradeCount());
    }

    // -----------------------------------------------------------------------
    // Auto-close on take profit
    // -----------------------------------------------------------------------

    @Test
    void autoClosesLongTradeWhenTakeProfitIsReached() {
        // entry=65000, atr=500, tp = 65000+1500 = 66500
        OpenTrade trade = manager.openTrade("BTCUSDT", SignalDirection.LONG, filled(1.0, 65000.0), 63000.0, 500.0);

        List<ClosedTrade> closedList = manager.onPriceTick("BTCUSDT", 66500.0, 500.0);

        assertEquals(1, closedList.size());
        ClosedTrade closed = closedList.get(0);
        assertEquals(TradeCloseReason.TAKE_PROFIT_HIT, closed.reason());
        assertTrue(closed.pnl() > 0.0, "TP hit should yield positive PnL");
    }

    // -----------------------------------------------------------------------
    // P&L calculation
    // -----------------------------------------------------------------------

    @Test
    void pnlIsCorrectForLongWin() {
        // Open trade, then manually close it (below TP so tick doesn't auto-close first)
        OpenTrade trade = manager.openTrade("BTCUSDT", SignalDirection.LONG, filled(2.0, 65000.0), 63000.0, 500.0);

        ClosedTrade ct = manager.closeTrade(trade.getTradeId(), 67000.0, TradeCloseReason.MANUAL);

        // pnl = (67000 - 65000) * 2 = 4000
        assertNotNull(ct);
        assertEquals(4000.0, ct.pnl(), 0.01);
        assertTrue(ct.pnlPercent() > 0.0);
    }

    @Test
    void pnlIsNegativeForLongLoss() {
        manager.openTrade("BTCUSDT", SignalDirection.LONG, filled(1.0, 65000.0), 63000.0, 500.0);
        List<ClosedTrade> closed = manager.onPriceTick("BTCUSDT", 62500.0, 500.0);
        assertFalse(closed.isEmpty());
        assertTrue(closed.get(0).pnl() < 0.0, "Stop-loss close on a LONG should be negative PnL");
    }

    // -----------------------------------------------------------------------
    // Signal reversal
    // -----------------------------------------------------------------------

    @Test
    void closesLongTradeOnShortSignal() {
        manager.openTrade("BTCUSDT", SignalDirection.LONG, filled(1.0, 65000.0), 63000.0, 500.0);
        List<ClosedTrade> closed = manager.evaluateForClose("BTCUSDT", 64000.0, SignalDirection.SHORT);

        assertEquals(1, closed.size());
        assertEquals(TradeCloseReason.SIGNAL_REVERSAL, closed.get(0).reason());
        assertEquals(0, manager.openTradeCount());
    }

    @Test
    void doesNotCloseOnNeutralSignal() {
        manager.openTrade("BTCUSDT", SignalDirection.LONG, filled(1.0, 65000.0), 63000.0, 500.0);
        List<ClosedTrade> closed = manager.evaluateForClose("BTCUSDT", 65500.0, SignalDirection.NEUTRAL);

        assertTrue(closed.isEmpty());
        assertEquals(1, manager.openTradeCount());
    }

    @Test
    void doesNotCloseOnSameDirectionSignal() {
        manager.openTrade("BTCUSDT", SignalDirection.LONG, filled(1.0, 65000.0), 63000.0, 500.0);
        List<ClosedTrade> closed = manager.evaluateForClose("BTCUSDT", 65500.0, SignalDirection.LONG);

        assertTrue(closed.isEmpty());
        assertEquals(1, manager.openTradeCount());
    }

    // -----------------------------------------------------------------------
    // Risk state integration
    // -----------------------------------------------------------------------

    @Test
    void notifiesRiskStateWhenTradeCloses() {
        manager.openTrade("BTCUSDT", SignalDirection.LONG, filled(1.0, 65000.0), 63000.0, 500.0);
        manager.closeTrade(
                manager.getOpenTrades("BTCUSDT").get(0).getTradeId(), 64000.0, TradeCloseReason.MANUAL);

        verify(riskState, times(1)).recordTradeClosed(any(ClosedTrade.class));
    }

    @Test
    void riskStateRecordTradeClosedDecrementsCount() {
        RiskStateService real = new RiskStateService();

        // Simulate one trade being recorded as active via execution
        ExecutionResult filledResult = filled(1.0, 65000.0);
        real.recordExecution(filledResult);
        assertEquals(1, real.currentConcurrentTrades());

        // Simulate closing at a loss
        ClosedTrade closed = new ClosedTrade(
                "t1", "BTCUSDT", SignalDirection.LONG, 1.0, 65000.0, 64000.0,
                -1000.0, -1.54, TradeCloseReason.STOP_LOSS_HIT,
                java.time.Instant.now(), java.time.Instant.now());

        real.recordTradeClosed(closed);

        assertEquals(0, real.currentConcurrentTrades());
        assertTrue(real.currentDailyLossPercent() > 0.0);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ExecutionResult filled(double quantity, double avgPrice) {
        return new ExecutionResult(
                ExecutionStatus.FILLED, quantity, quantity, avgPrice, avgPrice,
                0.0, 1, List.of(), "filled");
    }
}


