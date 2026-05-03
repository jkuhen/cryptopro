package com.kuhen.cryptopro.execution;

import com.kuhen.cryptopro.data.model.OrderBookSnapshot;
import com.kuhen.cryptopro.strategy.SignalDirection;

public record ExecutionRequest(
        String symbol,
        SignalDirection direction,
        boolean tradable,
        double sizeMultiplier,
        OrderBookSnapshot orderBookSnapshot,
        String accountType,
        String accountCode
) {

    public ExecutionRequest(
            String symbol,
            SignalDirection direction,
            boolean tradable,
            double sizeMultiplier,
            OrderBookSnapshot orderBookSnapshot
    ) {
        this(symbol, direction, tradable, sizeMultiplier, orderBookSnapshot, null, null);
    }
}

