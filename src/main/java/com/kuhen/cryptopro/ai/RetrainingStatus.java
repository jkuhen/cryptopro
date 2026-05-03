package com.kuhen.cryptopro.ai;

import java.time.Instant;

public record RetrainingStatus(
        boolean enabled,
        Instant lastRunAt,
        int totalRuns,
        boolean lastRunSuccessful,
        int samplesUsed,
        String datasetPath,
        String message
) {
}

