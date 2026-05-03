package com.kuhen.cryptopro.execution;

import com.kuhen.cryptopro.strategy.SignalDirection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LunoExecutionAdapter {

    private final LunoExecutionService lunoExecutionService;

    /** Default constructor for framework instantiation. */
    public LunoExecutionAdapter() {
        this.lunoExecutionService = null;
    }

    @Autowired
    public LunoExecutionAdapter(LunoExecutionService lunoExecutionService) {
        this.lunoExecutionService = lunoExecutionService;
    }

    public String submitMarketOrder(String symbol, SignalDirection direction, double quantity) {
        return lunoExecutionService.placeMarketOrder(symbol, direction, quantity);
    }

    public String submitMarketOrder(String symbol, SignalDirection direction, double quantity, String marketType) {
        return lunoExecutionService.placeMarketOrder(symbol, direction, quantity, marketType);
    }

    public String submitLimitOrder(String symbol, SignalDirection direction, double quantity, double limitPrice) {
        return lunoExecutionService.placeLimitOrder(symbol, direction, quantity, limitPrice);
    }

    public String submitLimitOrder(String symbol, SignalDirection direction, double quantity, double limitPrice, String marketType) {
        return lunoExecutionService.placeLimitOrder(symbol, direction, quantity, limitPrice, marketType);
    }

    public List<LunoBalance> fetchBalances() {
        return lunoExecutionService.fetchBalances();
    }

    /**
     * Fetches the current status of an order from the Luno exchange.
     * <p>
     * Luno GET /api/1/orders/{id} returns a JSON object containing:
     * <ul>
     *   <li>{@code state}   – PENDING | COMPLETE | CANCELLED | PARTIALLY_FILLED</li>
     *   <li>{@code base}    – total base volume placed</li>
     *   <li>{@code counter} – cumulative counter value traded (base × price per unit)</li>
     * </ul>
     * The average fill price is derived as {@code counter / base} when both are > 0.
     */
    public OrderStatusResult fetchOrderStatus(String orderId) {
        return lunoExecutionService.fetchOrderStatus(orderId);
    }

    public OrderStatusResult fetchOrderStatus(String orderId, String marketType) {
        return lunoExecutionService.fetchOrderStatus(orderId, marketType);
    }

    /**
     * Polls the exchange until the order reaches a terminal state or the
     * maximum number of poll attempts is exhausted.
     *
     * @param orderId The exchange order identifier to reconcile.
     * @return The last-known {@link OrderStatusResult}.  If the order has not
     *         reached a terminal state before the attempt limit, the last polled
     *         result is returned as-is so the caller can decide how to handle it.
     */
    public OrderStatusResult pollUntilTerminal(String orderId) {
        return lunoExecutionService.pollUntilTerminal(orderId);
    }

    public OrderStatusResult pollUntilTerminal(String orderId, String marketType) {
        return lunoExecutionService.pollUntilTerminal(orderId, marketType);
    }
}





