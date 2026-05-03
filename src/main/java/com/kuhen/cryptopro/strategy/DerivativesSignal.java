package com.kuhen.cryptopro.strategy;

public record DerivativesSignal(
        SignalDirection direction,
        double score,
        double openInterestDelta,
        double fundingRate,
        double priceDelta,
        String notes
) {
}

