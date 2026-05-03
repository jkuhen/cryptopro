package com.kuhen.cryptopro.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.kuhen.cryptopro.config.ExecutionProperties;
import com.kuhen.cryptopro.config.LunoProperties;
import com.kuhen.cryptopro.strategy.SignalDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LunoExecutionService {

    private static final Logger log = LoggerFactory.getLogger(LunoExecutionService.class);
    private static final String MARKET_SPOT = "SPOT";
    private static final String MARKET_FUTURES = "FUTURES";

    private final LunoApiClient lunoApiClient;
    private final LunoProperties lunoProperties;
    private final ExecutionProperties executionProperties;

    /** Default constructor for framework instantiation. */
    public LunoExecutionService() {
        this.lunoApiClient = null;
        this.lunoProperties = null;
        this.executionProperties = null;
    }

    @Autowired
    public LunoExecutionService(
            LunoApiClient lunoApiClient,
            LunoProperties lunoProperties,
            ExecutionProperties executionProperties
    ) {
        this.lunoApiClient = lunoApiClient;
        this.lunoProperties = lunoProperties;
        this.executionProperties = executionProperties;
    }

    public String placeMarketOrder(String symbol, SignalDirection direction, double quantity) {
        return placeMarketOrder(symbol, direction, quantity, MARKET_SPOT);
    }

    public String placeMarketOrder(String symbol, SignalDirection direction, double quantity, String marketType) {
        validateOrderInput(direction, quantity);

        Map<String, String> form = new LinkedHashMap<>();
        String path;
        String baseUrl;
        if (isFuturesMarket(marketType)) {
            if (!lunoProperties.isFuturesEnabled()) {
                throw new IllegalStateException("Luno futures execution is disabled by configuration");
            }
            form.put("symbol", resolveFuturesPair(symbol));
            form.put("side", direction == SignalDirection.LONG ? "BUY" : "SELL");
            form.put("quantity", Double.toString(quantity));
            form.put("order_type", "MARKET");
            path = lunoProperties.getFuturesMarketOrderPath();
            baseUrl = lunoProperties.getFuturesBaseUrl();
        } else {
            form.put("pair", resolvePair(symbol));
            form.put("type", direction == SignalDirection.LONG ? "BUY" : "SELL");
            form.put("base_volume", Double.toString(quantity));
            path = "/api/1/marketorder";
            baseUrl = lunoProperties.getBaseUrl();
        }

        try {
            JsonNode response = lunoApiClient.postForm(baseUrl, path, form);
            String orderId = response.path("order_id").asText("");
            if (orderId.isBlank()) {
                throw new IllegalStateException("Luno market order response missing order_id");
            }
            return orderId;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Luno market order interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Luno market order failed", ex);
        }
    }

    public String placeLimitOrder(String symbol, SignalDirection direction, double quantity, double limitPrice) {
        return placeLimitOrder(symbol, direction, quantity, limitPrice, MARKET_SPOT);
    }

    public String placeLimitOrder(String symbol, SignalDirection direction, double quantity, double limitPrice, String marketType) {
        validateOrderInput(direction, quantity);
        if (limitPrice <= 0.0) {
            throw new IllegalArgumentException("Limit price must be positive");
        }

        Map<String, String> form = new LinkedHashMap<>();
        String path;
        String baseUrl;
        if (isFuturesMarket(marketType)) {
            if (!lunoProperties.isFuturesEnabled()) {
                throw new IllegalStateException("Luno futures execution is disabled by configuration");
            }
            form.put("symbol", resolveFuturesPair(symbol));
            form.put("side", direction == SignalDirection.LONG ? "BUY" : "SELL");
            form.put("quantity", Double.toString(quantity));
            form.put("price", Double.toString(limitPrice));
            form.put("order_type", "LIMIT");
            form.put("time_in_force", "GTC");
            path = lunoProperties.getFuturesLimitOrderPath();
            baseUrl = lunoProperties.getFuturesBaseUrl();
        } else {
            form.put("pair", resolvePair(symbol));
            form.put("type", direction == SignalDirection.LONG ? "BID" : "ASK");
            form.put("volume", Double.toString(quantity));
            form.put("price", Double.toString(limitPrice));
            form.put("post_only", "false");
            path = "/api/1/postorder";
            baseUrl = lunoProperties.getBaseUrl();
        }

        try {
            JsonNode response = lunoApiClient.postForm(baseUrl, path, form);
            String orderId = response.path("order_id").asText("");
            if (orderId.isBlank()) {
                throw new IllegalStateException("Luno limit order response missing order_id");
            }
            return orderId;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Luno limit order interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Luno limit order failed", ex);
        }
    }

    public List<LunoBalance> fetchBalances() {
        try {
            JsonNode response = lunoApiClient.get("/api/1/balance", Map.of());
            List<LunoBalance> balances = new ArrayList<>();
            JsonNode list = response.path("balance");
            if (!list.isArray()) {
                return List.of();
            }

            for (JsonNode node : list) {
                balances.add(new LunoBalance(
                        node.path("asset").asText(""),
                        parseNumber(node.path("balance").asText("0")),
                        parseNumber(node.path("reserved").asText("0")),
                        parseNumber(node.path("unconfirmed").asText("0"))
                ));
            }
            return balances;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Fetching Luno balances interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Fetching Luno balances failed", ex);
        }
    }

    public OrderStatusResult fetchOrderStatus(String orderId) {
        return fetchOrderStatus(orderId, MARKET_SPOT);
    }

    public OrderStatusResult fetchOrderStatus(String orderId, String marketType) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }

        try {
            boolean futures = isFuturesMarket(marketType);
            String path = futures ? resolveFuturesOrderStatusPath(orderId) : "/api/1/orders/" + orderId;
            String baseUrl = futures ? lunoProperties.getFuturesBaseUrl() : lunoProperties.getBaseUrl();
            JsonNode response = lunoApiClient.get(baseUrl, path, Map.of());
            String rawState = response.path("state").asText("UNKNOWN");
            OrderState state = OrderState.fromLuno(rawState);

            double base = firstNumber(response, "filled_volume", "filled", "executed_volume", "base");
            double counter = parseNumber(response.path("counter").asText("0"));
            double averagePrice = firstNumber(response, "average_price", "avg_price");
            if (averagePrice <= 0.0 && base > 0.0 && counter > 0.0) {
                averagePrice = counter / base;
            }

            return new OrderStatusResult(orderId, state, base, averagePrice, rawState);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while fetching order status for {}", orderId);
            return OrderStatusResult.unknown(orderId);
        } catch (IOException ex) {
            log.warn("I/O error while fetching order status for {}: {}", orderId, ex.getMessage());
            return OrderStatusResult.unknown(orderId);
        }
    }

    public OrderStatusResult pollUntilTerminal(String orderId) {
        return pollUntilTerminal(orderId, MARKET_SPOT);
    }

    public OrderStatusResult pollUntilTerminal(String orderId, String marketType) {
        int maxAttempts = Math.max(1, executionProperties.getReconciliationMaxPollAttempts());
        long pollInterval = Math.max(100L, executionProperties.getReconciliationPollIntervalMs());

        OrderStatusResult last = OrderStatusResult.unknown(orderId);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            last = fetchOrderStatus(orderId, marketType);
            if (last.state().isTerminal()) {
                return last;
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(pollInterval);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return last;
                }
            }
        }

        return last;
    }

    private void validateOrderInput(SignalDirection direction, double quantity) {
        if (direction == null || direction == SignalDirection.NEUTRAL) {
            throw new IllegalArgumentException("Direction must be LONG or SHORT");
        }
        if (quantity <= 0.0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    private String resolvePair(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase();
        return lunoProperties.getSymbolMap().getOrDefault(normalized, lunoProperties.getDefaultPair());
    }

    private String resolveFuturesPair(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase();
        return lunoProperties.getFuturesSymbolMap().getOrDefault(normalized, lunoProperties.getDefaultFuturesPair());
    }

    private boolean isFuturesMarket(String marketType) {
        return MARKET_FUTURES.equalsIgnoreCase(String.valueOf(marketType));
    }

    private String resolveFuturesOrderStatusPath(String orderId) {
        String template = lunoProperties.getFuturesOrderStatusPathTemplate();
        if (template == null || template.isBlank()) {
            return "/api/2/futures/orders/" + orderId;
        }
        if (template.contains("{orderId}")) {
            return template.replace("{orderId}", orderId);
        }
        String trimmed = template.endsWith("/") ? template.substring(0, template.length() - 1) : template;
        return trimmed + "/" + orderId;
    }

    private double parseNumber(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private double firstNumber(JsonNode node, String... fields) {
        for (String field : fields) {
            double parsed = parseNumber(node.path(field).asText("0"));
            if (parsed > 0.0) {
                return parsed;
            }
        }
        return 0.0;
    }
}

