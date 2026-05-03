package com.kuhen.cryptopro.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionStartupValidatorTest {

    @Test
    void doesNothingWhenProviderIsNotLuno() {
        ExecutionProperties execution = new ExecutionProperties();
        execution.setProvider("paper");
        execution.setMarketType("FUTURES");

        LunoProperties luno = new LunoProperties();
        ExecutionStartupValidator validator = new ExecutionStartupValidator(execution, luno);

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void doesNothingWhenMarketTypeIsNotFutures() {
        ExecutionProperties execution = new ExecutionProperties();
        execution.setProvider("luno");
        execution.setMarketType("SPOT");

        LunoProperties luno = new LunoProperties();
        ExecutionStartupValidator validator = new ExecutionStartupValidator(execution, luno);

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void failsWhenLunoFuturesIsNotEnabled() {
        LunoProperties luno = validLunoFutures();
        luno.setFuturesEnabled(false);
        ExecutionStartupValidator validator = new ExecutionStartupValidator(lunoFuturesExecution(), luno);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);
        assertTrue(ex.getMessage().contains("cryptopro.luno.futures-enabled must be true"));
    }

    @Test
    void failsWhenRequiredLunoFuturesFieldsAreBlank() {
        ExecutionProperties execution = lunoFuturesExecution();
        LunoProperties luno = new LunoProperties();
        luno.setFuturesEnabled(true);
        luno.setFuturesBaseUrl(" ");
        luno.setDefaultFuturesPair(" ");
        luno.setFuturesMarketOrderPath(" ");
        luno.setFuturesLimitOrderPath(" ");
        luno.setFuturesOrderStatusPathTemplate(" ");
        luno.setFuturesSymbolMap(Map.of());

        ExecutionStartupValidator validator = new ExecutionStartupValidator(execution, luno);
        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);

        String message = ex.getMessage();
        assertTrue(message.contains("cryptopro.luno.futures-base-url must not be blank"));
        assertTrue(message.contains("cryptopro.luno.default-futures-pair must not be blank"));
        assertTrue(message.contains("cryptopro.luno.futures-market-order-path must not be blank"));
        assertTrue(message.contains("cryptopro.luno.futures-limit-order-path must not be blank"));
        assertTrue(message.contains("cryptopro.luno.futures-order-status-path-template must not be blank"));
        assertTrue(message.contains("cryptopro.luno.futures-symbol-map must define at least one symbol mapping"));
    }

    @Test
    void passesWhenLunoFuturesConfigIsComplete() {
        ExecutionStartupValidator validator = new ExecutionStartupValidator(lunoFuturesExecution(), validLunoFutures());
        assertDoesNotThrow(validator::validate);
    }

    private static ExecutionProperties lunoFuturesExecution() {
        ExecutionProperties execution = new ExecutionProperties();
        execution.setProvider("luno");
        execution.setMarketType("FUTURES");
        return execution;
    }

    private static LunoProperties validLunoFutures() {
        LunoProperties luno = new LunoProperties();
        luno.setFuturesEnabled(true);
        luno.setFuturesBaseUrl("https://futures.luno.com");
        luno.setDefaultFuturesPair("BTCUSDT-PERP");
        luno.setFuturesMarketOrderPath("/api/2/futures/marketorder");
        luno.setFuturesLimitOrderPath("/api/2/futures/postorder");
        luno.setFuturesOrderStatusPathTemplate("/api/2/futures/orders/{orderId}");
        luno.setFuturesSymbolMap(Map.of("BTCUSDT", "BTCUSDT-PERP"));
        return luno;
    }
}


