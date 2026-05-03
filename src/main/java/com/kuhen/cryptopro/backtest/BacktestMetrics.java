package com.kuhen.cryptopro.backtest;

public record BacktestMetrics(
        int totalTrades,
        int wins,
        int losses,
        double winRatePercent,
        double grossProfit,
        double grossLoss,
        double netProfit,
        double profitFactor,
        double maxDrawdown,
        double maxDrawdownPercent,
        double averageTradePnl
) {
}

