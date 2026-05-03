package com.kuhen.cryptopro.ops;

import java.util.List;

public record DashboardOverviewResponse(
        DashboardKpis kpis,
        SignalSummaryResponse signalSummary,
        PaperPortfolioSnapshot portfolio,
        AdministrationInfo administration,
        List<TransactionLogEntry> recentTransactions,
        List<ErrorLogEntry> recentErrors
) {
}

