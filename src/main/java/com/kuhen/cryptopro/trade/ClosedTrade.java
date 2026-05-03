package com.kuhen.cryptopro.trade;

import com.kuhen.cryptopro.strategy.SignalDirection;

import java.time.Instant;

/**
 * Immutable snapshot of a trade after its lifecycle has ended.
 */
public record ClosedTrade(
        String tradeId,
        String symbol,
        SignalDirection direction,
        double quantity,
        double entryPrice,
        double closePrice,
        double pnl,
        double pnlPercent,
        TradeCloseReason reason,
        Instant openedAt,
        Instant closedAt
) {
    /** Convenience factory from an in-flight {@link OpenTrade}. */
    public static ClosedTrade of(OpenTrade trade, double closePrice, TradeCloseReason reason) {
        double pnl = trade.realisedPnl(closePrice);
        double pnlPercent = trade.getEntryPrice() > 0.0
                ? (pnl / (trade.getEntryPrice() * trade.getQuantity())) * 100.0
                : 0.0;
        return new ClosedTrade(
                trade.getTradeId(),
                trade.getSymbol(),
                trade.getDirection(),
                trade.getQuantity(),
                trade.getEntryPrice(),
                closePrice,
                pnl,
                pnlPercent,
                reason,
                trade.getOpenedAt(),
                Instant.now()
        );
    }
}

