package com.kuhen.cryptopro.ops;

import java.time.Instant;
import java.util.List;

public record TrendReadinessResponse(
        Instant generatedAt,
        int hours,
        List<TrendReadinessRow> rows
) {
}

