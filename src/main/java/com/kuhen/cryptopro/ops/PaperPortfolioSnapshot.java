package com.kuhen.cryptopro.ops;

import java.util.List;

public record PaperPortfolioSnapshot(
        double cashBalance,
        double equity,
        double initialCash,
        double totalRealizedPnl,
        double totalUnrealizedPnl,
        double totalFees,
        List<PaperPositionView> positions,
        List<EquityPoint> equityCurve
) {
}

