package com.kuhen.cryptopro.data.model;

import java.time.Instant;

public record FundingRate(
        String symbol,
        Instant fundingTime,
        double rate
) {
}

