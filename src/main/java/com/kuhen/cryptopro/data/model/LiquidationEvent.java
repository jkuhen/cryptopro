package com.kuhen.cryptopro.data.model;

import java.time.Instant;

public record LiquidationEvent(
        String symbol,
        Instant timestamp,
        LiquidationSide side,
        double price,
        double sizeUsd
) {
}

