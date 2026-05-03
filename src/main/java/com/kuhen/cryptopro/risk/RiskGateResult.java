package com.kuhen.cryptopro.risk;

import java.util.List;

public record RiskGateResult(
        boolean passed,
        List<String> reasons,
        double spreadBps,
        long latencyMs,
        long feedAgeSeconds
) {
}

