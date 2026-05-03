package com.kuhen.cryptopro.trade;

import com.kuhen.cryptopro.strategy.SignalDirection;

import java.time.Instant;
import java.util.UUID;

/**
 * In-memory representation of an open trade being tracked by the lifecycle manager.
 * Mutable because trailing stop and high/low watermark are updated on every price tick.
 */
public class OpenTrade {

    private final String tradeId;
    private final String symbol;
    private final SignalDirection direction;
    private final double quantity;
    private final double entryPrice;
    private final double initialStopLoss;
    private final double takeProfitPrice;
    private final double atrAtEntry;
    private final Instant openedAt;

    /** Tracks the best price seen since entry — used to ratchet the trailing stop. */
    private double extremePrice;

    /** Current trailing stop level; only ever moves in the direction of profit. */
    private double trailingStopLoss;

    /** Optional DB trade ID linked after persistence. */
    private Long dbTradeId;

    public OpenTrade(
            String symbol,
            SignalDirection direction,
            double quantity,
            double entryPrice,
            double initialStopLoss,
            double takeProfitPrice,
            double atrAtEntry
    ) {
        this.tradeId        = UUID.randomUUID().toString();
        this.symbol         = symbol;
        this.direction      = direction;
        this.quantity       = quantity;
        this.entryPrice     = entryPrice;
        this.initialStopLoss = initialStopLoss;
        this.trailingStopLoss = initialStopLoss;
        this.takeProfitPrice = takeProfitPrice;
        this.atrAtEntry     = atrAtEntry;
        this.extremePrice   = entryPrice;
        this.openedAt       = Instant.now();
    }

    // -----------------------------------------------------------------------
    // Trailing stop state management
    // -----------------------------------------------------------------------

    /**
     * Updates the trailing stop given the latest price tick and current ATR.
     * The stop only ratchets in the direction of profit; it never widens.
     *
     * @param currentPrice latest market price
     * @param atr          latest ATR reading used to compute stop distance
     * @param atrMultiplier distance multiplier (e.g. 2.0)
     */
    public void updateTrailingStop(double currentPrice, double atr, double atrMultiplier) {
        if (atr <= 0.0) {
            return;
        }
        double distance = atr * atrMultiplier;
        if (direction == SignalDirection.LONG) {
            if (currentPrice > extremePrice) {
                extremePrice = currentPrice;
            }
            double candidate = extremePrice - distance;
            if (candidate > trailingStopLoss) {
                trailingStopLoss = candidate;
            }
        } else if (direction == SignalDirection.SHORT) {
            if (currentPrice < extremePrice) {
                extremePrice = currentPrice;
            }
            double candidate = extremePrice + distance;
            if (candidate < trailingStopLoss) {
                trailingStopLoss = candidate;
            }
        }
    }

    /** Returns true if the current price has crossed into the trailing stop level. */
    public boolean isStopLossHit(double currentPrice) {
        if (direction == SignalDirection.LONG) {
            return currentPrice <= trailingStopLoss;
        }
        if (direction == SignalDirection.SHORT) {
            return currentPrice >= trailingStopLoss;
        }
        return false;
    }

    /** Returns true if the current price has reached or exceeded the take-profit level. */
    public boolean isTakeProfitHit(double currentPrice) {
        if (takeProfitPrice <= 0.0) {
            return false;
        }
        if (direction == SignalDirection.LONG) {
            return currentPrice >= takeProfitPrice;
        }
        if (direction == SignalDirection.SHORT) {
            return currentPrice <= takeProfitPrice;
        }
        return false;
    }

    /** Returns true if the supplied signal direction opposes this trade. */
    public boolean isSignalReversal(SignalDirection newDirection) {
        if (newDirection == SignalDirection.NEUTRAL) {
            return false;
        }
        return direction != newDirection;
    }

    // -----------------------------------------------------------------------
    // Computed P&L
    // -----------------------------------------------------------------------

    public double unrealisedPnl(double currentPrice) {
        if (direction == SignalDirection.LONG) {
            return (currentPrice - entryPrice) * quantity;
        }
        if (direction == SignalDirection.SHORT) {
            return (entryPrice - currentPrice) * quantity;
        }
        return 0.0;
    }

    public double realisedPnl(double closePrice) {
        return unrealisedPnl(closePrice);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public String getTradeId()           { return tradeId; }
    public String getSymbol()            { return symbol; }
    public SignalDirection getDirection(){ return direction; }
    public double getQuantity()          { return quantity; }
    public double getEntryPrice()        { return entryPrice; }
    public double getInitialStopLoss()   { return initialStopLoss; }
    public double getTrailingStopLoss()  { return trailingStopLoss; }
    public double getTakeProfitPrice()   { return takeProfitPrice; }
    public double getAtrAtEntry()        { return atrAtEntry; }
    public Instant getOpenedAt()         { return openedAt; }
    public double getExtremePrice()      { return extremePrice; }
    public Long getDbTradeId()           { return dbTradeId; }
    public void setDbTradeId(Long id)    { this.dbTradeId = id; }

    @Override
    public String toString() {
        return "OpenTrade{tradeId=" + tradeId + ", symbol=" + symbol +
               ", direction=" + direction + ", qty=" + quantity +
               ", entry=" + entryPrice + ", trailStop=" + trailingStopLoss +
               ", tp=" + takeProfitPrice + "}";
    }
}

