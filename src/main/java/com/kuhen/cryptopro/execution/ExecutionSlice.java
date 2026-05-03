package com.kuhen.cryptopro.execution;

public record ExecutionSlice(
        int index,
        double requestedQuantity,
        double filledQuantity,
        double limitPrice,
        double averagePrice,
        ExecutionStatus status,
        String notes
) {
}

