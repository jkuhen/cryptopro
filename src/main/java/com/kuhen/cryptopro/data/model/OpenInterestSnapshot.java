package com.kuhen.cryptopro.data.model;

import java.time.Instant;

public record OpenInterestSnapshot(
        String symbol,
        Instant timestamp,
        double openInterestUsd
) {
}

