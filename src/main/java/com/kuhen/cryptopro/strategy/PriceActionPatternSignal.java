package com.kuhen.cryptopro.strategy;

public record PriceActionPatternSignal(
        boolean detected,
        SignalDirection direction,
        double strength,
        double referenceLevel,
        double triggerLevel,
        String notes
) {
    public static PriceActionPatternSignal none(String notes) {
        return new PriceActionPatternSignal(false, SignalDirection.NEUTRAL, 0.0, 0.0, 0.0, notes);
    }
}

