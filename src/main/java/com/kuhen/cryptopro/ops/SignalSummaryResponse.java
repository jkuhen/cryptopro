package com.kuhen.cryptopro.ops;

import java.util.List;

public record SignalSummaryResponse(
        int totalSignals,
        int wins,
        int losses,
        double overallWinRatePercent,
        List<ConditionWinRate> winRateByCondition,
        List<SignalLogEntry> recentSignals
) {
}

