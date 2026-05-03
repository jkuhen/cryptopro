package com.kuhen.cryptopro.demo;

import com.kuhen.cryptopro.ai.MarketRegime;
import com.kuhen.cryptopro.execution.ExecutionSlice;
import com.kuhen.cryptopro.execution.ExecutionStatus;
import com.kuhen.cryptopro.preprocess.SessionType;
import com.kuhen.cryptopro.preprocess.VolatilityRegime;
import com.kuhen.cryptopro.strategy.SignalDirection;

import java.util.List;

public record PipelineDemoResponse(
        String symbol,
        SessionType sessionType,
        VolatilityRegime volatilityRegime,
        double dataQualityScore,
        double orderBookSpread,
        double biasScore,
        double smcScore,
        double derivativesScore,
        double finalScore,
        SignalDirection direction,
        double sizeMultiplier,
        boolean tradable,
        boolean riskPassed,
        List<String> riskReasons,
        long feedLatencyMs,
        long feedAgeSeconds,
        double spreadBps,
        double aiProbabilityOfSuccess,
        double aiConfidence,
        MarketRegime detectedRegime,
        double regimeConfidence,
        String aiModel,
        String aiModelVersion,
        String aiNotes,
        boolean riskEngineAllowed,
        double riskAdjustedSizeMultiplier,
        double riskAtr,
        double riskStopLossPrice,
        double riskPerTradePercent,
        double riskDailyLossPercent,
        double riskDailyLossCapPercent,
        int riskConcurrentTrades,
        int riskMaxConcurrentTrades,
        double riskMaxAllowedQuantity,
        ExecutionStatus executionStatus,
        double executionRequestedQuantity,
        double executionFilledQuantity,
        double executionLimitPrice,
        double executionAverageFillPrice,
        double executionSlippageBps,
        int executionPartialEntries,
        List<ExecutionSlice> executionSlices,
        String executionNotes,
        String rationale
) {
}

