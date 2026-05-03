package com.kuhen.cryptopro.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuhen.cryptopro.config.ExecutionProperties;
import com.kuhen.cryptopro.config.LunoProperties;
import com.kuhen.cryptopro.strategy.SignalDirection;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LunoExecutionServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void placesMarketOrderAndReturnsOrderId() throws Exception {
        LunoApiClient apiClient = mock(LunoApiClient.class);
        when(apiClient.postForm(eq("https://api.luno.com"), eq("/api/1/marketorder"), anyMap()))
                .thenReturn(json("{\"order_id\":\"MO-123\"}"));

        LunoExecutionService service = new LunoExecutionService(apiClient, lunoProperties(), executionProperties());
        String orderId = service.placeMarketOrder("BTCUSDT", SignalDirection.LONG, 0.2);

        assertEquals("MO-123", orderId);
    }

    @Test
    void fetchesBalancesFromExchange() throws Exception {
        LunoApiClient apiClient = mock(LunoApiClient.class);
        when(apiClient.get(eq("/api/1/balance"), eq(Map.of()))).thenReturn(json("""
                {
                  "balance": [
                    {"asset":"XBT","balance":"1.5","reserved":"0.1","unconfirmed":"0.0"}
                  ]
                }
                """));

        LunoExecutionService service = new LunoExecutionService(apiClient, lunoProperties(), executionProperties());
        List<LunoBalance> balances = service.fetchBalances();

        assertEquals(1, balances.size());
        assertEquals("XBT", balances.get(0).asset());
        assertEquals(1.5, balances.get(0).balance());
    }

    @Test
    void tracksOrderStatusUsingFilledAndAveragePriceFields() throws Exception {
        LunoApiClient apiClient = mock(LunoApiClient.class);
        when(apiClient.get(eq("https://api.luno.com"), eq("/api/1/orders/ORD-1"), eq(Map.of()))).thenReturn(json("""
                {
                  "state":"PARTIALLY_FILLED",
                  "filled_volume":"0.25",
                  "average_price":"65000"
                }
                """));

        LunoExecutionService service = new LunoExecutionService(apiClient, lunoProperties(), executionProperties());
        OrderStatusResult status = service.fetchOrderStatus("ORD-1");

        assertEquals(OrderState.PARTIALLY_FILLED, status.state());
        assertEquals(0.25, status.filledQuantity());
        assertEquals(65000.0, status.averagePrice());
    }

    @Test
    void fallsBackToUnknownStatusWhenApiThrowsIoError() throws Exception {
        LunoApiClient apiClient = mock(LunoApiClient.class);
        when(apiClient.get(eq("https://api.luno.com"), eq("/api/1/orders/ORD-IO"), eq(Map.of()))).thenThrow(new IOException("network"));

        LunoExecutionService service = new LunoExecutionService(apiClient, lunoProperties(), executionProperties());
        OrderStatusResult status = service.fetchOrderStatus("ORD-IO");

        assertEquals(OrderState.UNKNOWN, status.state());
        assertTrue(status.rawState().contains("UNKNOWN"));
    }

    @Test
    void routesFuturesMarketOrdersToConfiguredFuturesEndpoint() throws Exception {
        LunoApiClient apiClient = mock(LunoApiClient.class);
        LunoProperties luno = lunoProperties();
        luno.setFuturesEnabled(true);
        luno.setFuturesBaseUrl("https://futures.luno.com");
        when(apiClient.postForm(eq("https://futures.luno.com"), eq("/api/2/futures/marketorder"), anyMap()))
                .thenReturn(json("{\"order_id\":\"FUT-123\"}"));

        LunoExecutionService service = new LunoExecutionService(apiClient, luno, executionProperties());
        String orderId = service.placeMarketOrder("BTCUSDT", SignalDirection.LONG, 0.4, "FUTURES");

        assertEquals("FUT-123", orderId);
    }

    @Test
    void rejectsFuturesOrderWhenFuturesExecutionIsDisabled() {
        LunoApiClient apiClient = mock(LunoApiClient.class);
        LunoExecutionService service = new LunoExecutionService(apiClient, lunoProperties(), executionProperties());

        assertThrows(IllegalStateException.class,
                () -> service.placeMarketOrder("BTCUSDT", SignalDirection.LONG, 0.2, "FUTURES"));
    }

    private LunoProperties lunoProperties() {
        LunoProperties properties = new LunoProperties();
        properties.setDefaultPair("BTCUSDT");
        properties.setSymbolMap(Map.of("BTCUSDT", "BTCUSDT"));
        properties.setDefaultFuturesPair("BTCUSDT-PERP");
        properties.setFuturesSymbolMap(Map.of("BTCUSDT", "BTCUSDT-PERP"));
        return properties;
    }

    private ExecutionProperties executionProperties() {
        ExecutionProperties properties = new ExecutionProperties();
        properties.setReconciliationPollIntervalMs(5);
        properties.setReconciliationMaxPollAttempts(2);
        return properties;
    }

    private JsonNode json(String raw) throws IOException {
        return MAPPER.readTree(raw);
    }
}

