package com.kuhen.cryptopro.execution;

import com.kuhen.cryptopro.strategy.SignalDirection;

import java.util.List;

public record ExecutionSimulationRequest(
        String symbol,
        SignalDirection direction,
        boolean tradable,
        double quantity,
        int maxPartialEntries,
        double slippageToleranceBps,
        double limitOffsetBps,
        List<ExecutionSimulationLevelRequest> bids,
        List<ExecutionSimulationLevelRequest> asks
) {
}

