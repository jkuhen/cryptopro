package com.kuhen.cryptopro.ops;

public record DashboardKpis(
        int totalTransactions,
        int successfulTransactions,
        int rejectedTransactions,
        double successRatePercent,
        double averageSlippageBps,
        int errorCount
) {
}

