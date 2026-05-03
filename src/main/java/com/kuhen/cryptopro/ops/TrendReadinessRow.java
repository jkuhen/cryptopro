package com.kuhen.cryptopro.ops;

import java.time.Instant;

public record TrendReadinessRow(
        String symbol,
        long h1CandlesLastHours,
        long h1IndicatorsLastHours,
        double h1IndicatorCoveragePct,
        long h1Ema200ReadyLastHours,
        Instant firstH1Ema200At,
        String status
) {
}

