package com.kuhen.cryptopro.risk;

import com.kuhen.cryptopro.execution.ExecutionResult;
import com.kuhen.cryptopro.execution.ExecutionStatus;
import com.kuhen.cryptopro.trade.ClosedTrade;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class RiskStateService {

    private LocalDate tradingDate = LocalDate.now();
    private double dailyLossPercent = 0.0;
    private int concurrentTrades = 0;

    public synchronized double currentDailyLossPercent() {
        resetIfNewDay();
        return dailyLossPercent;
    }

    public synchronized int currentConcurrentTrades() {
        resetIfNewDay();
        return concurrentTrades;
    }

    public synchronized void recordExecution(ExecutionResult result) {
        resetIfNewDay();
        if (result.status() == ExecutionStatus.FILLED || result.status() == ExecutionStatus.PARTIAL) {
            concurrentTrades++;
            // Simulate a tiny realized cost component in demo mode.
            dailyLossPercent += Math.min(0.2, result.slippageBps() / 100.0);
        }
        if (result.status() == ExecutionStatus.REJECTED && result.notes().toLowerCase().contains("slippage")) {
            dailyLossPercent += 0.05;
        }
    }

    /**
     * Called by {@link com.kuhen.cryptopro.trade.TradeLifecycleManager} when a trade is
     * closed (stop hit, take profit, signal reversal, or manual).
     * <ul>
     *   <li>Decrements the concurrent trade count.</li>
     *   <li>Accumulates realised loss into the daily loss tracker when PnL is negative.</li>
     * </ul>
     */
    public synchronized void recordTradeClosed(ClosedTrade closed) {
        resetIfNewDay();
        concurrentTrades = Math.max(0, concurrentTrades - 1);
        if (closed.pnl() < 0.0) {
            double lossPercent = Math.abs(closed.pnlPercent());
            dailyLossPercent += lossPercent;
        }
    }

    private void resetIfNewDay() {
        LocalDate now = LocalDate.now();
        if (!now.equals(tradingDate)) {
            tradingDate = now;
            dailyLossPercent = 0.0;
            concurrentTrades = 0;
        }
    }
}

