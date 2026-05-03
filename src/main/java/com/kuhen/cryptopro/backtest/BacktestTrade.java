package com.kuhen.cryptopro.backtest;

import com.kuhen.cryptopro.strategy.SignalDirection;

import java.time.Instant;

public record BacktestTrade(
        SignalDirection direction,
        Instant entryTime,
        Instant exitTime,
        double entryPrice,
        double exitPrice,
        double quantity,
        double pnl,
        String exitReason
) {
}

