package com.kuhen.cryptopro.data.dto;

import java.time.Instant;

/**
 * Summary of one scheduled Binance sync cycle for a single symbol.
 */
public record BinanceMarketSyncResultDto(
        String symbol,
        Instant syncedAt,
        int persistedM1,
        int persistedM5,
        int persistedM15,
        int persistedH1,
        String notes
) {
}

