package com.kuhen.cryptopro.ops;

public record ConditionWinRate(
        String condition,
        int total,
        int wins,
        int losses,
        double winRatePercent
) {
}

