package com.kuhen.cryptopro.strategy;

public record SmcSignal(
        PriceActionPatternSignal liquiditySweepSignal,
        PriceActionPatternSignal breakOfStructureSignal,
        PriceActionPatternSignal fairValueGapSignal,
        double score,
        SignalDirection direction,
        String notes
) {
    public boolean liquiditySweep() {
        return liquiditySweepSignal.detected();
    }

    public boolean breakOfStructure() {
        return breakOfStructureSignal.detected();
    }

    public boolean fairValueGap() {
        return fairValueGapSignal.detected();
    }
}

