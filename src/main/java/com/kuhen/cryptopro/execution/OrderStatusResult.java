package com.kuhen.cryptopro.execution;

/**
 * Snapshot of an order's state as reconciled from the exchange.
 *
 * @param orderId        Exchange-assigned order identifier.
 * @param state          Normalised {@link OrderState}.
 * @param filledQuantity Base volume that has actually been matched.
 * @param averagePrice   Weighted-average fill price (counter / base).
 *                       Zero when nothing has been filled.
 * @param rawState       The raw state string returned by the exchange,
 *                       retained for diagnostics / logging.
 */
public record OrderStatusResult(
        String orderId,
        OrderState state,
        double filledQuantity,
        double averagePrice,
        String rawState
) {

    /** Convenience factory for an order that could not be fetched. */
    public static OrderStatusResult unknown(String orderId) {
        return new OrderStatusResult(orderId, OrderState.UNKNOWN, 0.0, 0.0, "UNKNOWN");
    }
}

