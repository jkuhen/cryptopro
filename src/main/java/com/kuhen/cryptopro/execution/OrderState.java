package com.kuhen.cryptopro.execution;

/**
 * Canonical order lifecycle states as reported by the Luno exchange.
 * Luno uses the string values; UNKNOWN acts as a safe default for
 * any value not yet mapped.
 */
public enum OrderState {

    /** Order has been accepted but not yet matched. */
    PENDING,

    /** Order has been fully matched/filled. */
    COMPLETE,

    /** Order was partially filled before cancellation. */
    PARTIALLY_FILLED,

    /** Order was cancelled with zero fills. */
    CANCELLED,

    /** Catch-all for unrecognised exchange states. */
    UNKNOWN;

    /**
     * Map a raw Luno state string to an {@link OrderState}.
     * Luno states observed in the wild: PENDING, COMPLETE, CANCELLED.
     */
    public static OrderState fromLuno(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        return switch (raw.toUpperCase()) {
            case "PENDING"          -> PENDING;
            case "COMPLETE"         -> COMPLETE;
            case "PARTIALLY_FILLED" -> PARTIALLY_FILLED;
            case "CANCELLED"        -> CANCELLED;
            default                 -> UNKNOWN;
        };
    }

    /** Returns {@code true} when no further state transitions are expected. */
    public boolean isTerminal() {
        return this == COMPLETE || this == PARTIALLY_FILLED || this == CANCELLED;
    }
}

