package com.kuhen.cryptopro.ops;

import java.time.Instant;

public record ErrorLogEntry(
        Instant timestamp,
        String source,
        String message,
        String details
) {
}

