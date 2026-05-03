package com.kuhen.cryptopro.risk;

import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.strategy.SignalDirection;

import java.util.List;

public record RiskManagementRequest(
        String symbol,
        SignalDirection direction,
        boolean tradable,
        double proposedSizeMultiplier,
        double currentPrice,
        List<Candle> atrCandles
) {
}

