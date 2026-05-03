package com.kuhen.cryptopro.strategy;

import com.kuhen.cryptopro.data.entity.SignalEntity;

/**
 * Record representing a generated trading signal.
 *
 * @param symbol the trading symbol (e.g., "BTCUSDT")
 * @param signalType the signal direction (BUY or SELL)
 * @param direction the composite trend direction
 * @param confidenceScore the confidence score (0.0 to 1.0)
 * @param rationale detailed breakdown of how the signal was generated
 */
public record StrategySignal(
    String symbol,
    SignalEntity.SignalTypeEnum signalType,
    SignalDirection direction,
    Double confidenceScore,
    MultiTimeframeStrategyEngine.SignalRationale rationale
) {
    @Override
    public String toString() {
        return "StrategySignal{" +
            "symbol='" + symbol + '\'' +
            ", signalType=" + signalType +
            ", direction=" + direction +
            ", confidenceScore=" + String.format("%.3f", confidenceScore) +
            ", rationale=" + rationale +
            '}';
    }
}

