package com.kuhen.cryptopro.backtest;

public record BacktestRequest(
        String symbol,
        String historicalCsvPath,
        int maxCandles,
        int warmupCandles,
        int atrPeriod,
        double quantity,
        double stopLossAtrMultiplier,
        double takeProfitAtrMultiplier
) {
    public static BacktestRequest defaults(String symbol, String historicalCsvPath) {
        return new BacktestRequest(symbol, historicalCsvPath, 0, 120, 14, 1.0, 2.0, 3.0);
    }
}

