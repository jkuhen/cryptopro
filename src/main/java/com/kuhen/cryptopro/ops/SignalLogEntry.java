package com.kuhen.cryptopro.ops;

import com.kuhen.cryptopro.ai.MarketRegime;
import com.kuhen.cryptopro.strategy.SignalDirection;

import java.time.Instant;

public record SignalLogEntry(
        Instant timestamp,
        String symbol,
        SignalDirection direction,
        double finalScore,
        double aiProbability,
        boolean liquiditySweep,
        boolean volumeSpike,
        boolean oiConfirmation,
        MarketRegime regime,
        double regimeConfidence,
        Double entryPrice,
        Double stopLoss,
        Double takeProfit,
        SignalOutcome outcome,
        String notes,
        String executionStatus,
        String accountCode,
        String provider,
        String tradeId
) {

    public SignalLogEntry(
            Instant timestamp,
            String symbol,
            SignalDirection direction,
            double finalScore,
            double aiProbability,
            boolean liquiditySweep,
            boolean volumeSpike,
            boolean oiConfirmation,
            MarketRegime regime,
            double regimeConfidence,
            Double entryPrice,
            Double stopLoss,
            Double takeProfit,
            SignalOutcome outcome,
            String notes
    ) {
        this(timestamp, symbol, direction, finalScore, aiProbability, liquiditySweep, volumeSpike,
                oiConfirmation, regime, regimeConfidence, entryPrice, stopLoss, takeProfit,
                outcome, notes, null, null, null, null);
    }
}

