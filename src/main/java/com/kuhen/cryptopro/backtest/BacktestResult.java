package com.kuhen.cryptopro.backtest;

import com.kuhen.cryptopro.data.model.Candle;

import java.util.List;

public record BacktestResult(
        String symbol,
        int replayedCandles,
        BacktestMetrics metrics,
        List<BacktestTrade> trades,
        List<Candle> candles
) {
}

