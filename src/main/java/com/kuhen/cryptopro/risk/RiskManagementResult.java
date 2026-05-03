package com.kuhen.cryptopro.risk;

import java.util.List;

public record RiskManagementResult(
        boolean allowed,
        List<String> reasons,
        double adjustedSizeMultiplier,
        double atr,
        double stopLossPrice,
        double riskPerTradePercent,
        double dailyLossPercent,
        double dailyLossCapPercent,
        int concurrentTrades,
        int maxConcurrentTrades,
        double maxAllowedQuantity
) {
}

