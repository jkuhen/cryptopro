package com.kuhen.cryptopro.execution;

import java.util.List;

public record ExecutionResult(
        ExecutionStatus status,
        double requestedQuantity,
        double filledQuantity,
        double limitPrice,
        double averageFillPrice,
        double slippageBps,
        int partialEntries,
        List<ExecutionSlice> slices,
        String notes,
        String provider,
        String accountType,
        String accountCode
) {

    public ExecutionResult(
            ExecutionStatus status,
            double requestedQuantity,
            double filledQuantity,
            double limitPrice,
            double averageFillPrice,
            double slippageBps,
            int partialEntries,
            List<ExecutionSlice> slices,
            String notes
    ) {
        this(status, requestedQuantity, filledQuantity, limitPrice, averageFillPrice, slippageBps,
                partialEntries, slices, notes, null, null, null);
    }

    public static ExecutionResult notSent(String notes) {
        return new ExecutionResult(
                ExecutionStatus.NOT_SENT,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0,
                List.of(),
                notes,
                null,
                null,
                null
        );
    }
}

