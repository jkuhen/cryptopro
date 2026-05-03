package com.kuhen.cryptopro.execution;

import com.kuhen.cryptopro.config.ExecutionProperties;
import com.kuhen.cryptopro.data.model.OrderBookLevel;
import com.kuhen.cryptopro.data.model.OrderBookSnapshot;
import com.kuhen.cryptopro.strategy.SignalDirection;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionEngineServiceTest {

    @Test
    void usesLimitLogicAndReturnsFilledStatusWhenDepthIsEnough() {
        ExecutionProperties properties = new ExecutionProperties();
        properties.setBaseOrderQuantity(1.0);
        properties.setMaxPartialEntries(2);
        properties.setSlippageToleranceBps(50.0);
        properties.setLimitOffsetBps(2.0);

        ExecutionEngineService engine = new ExecutionEngineService(properties);
        ExecutionResult result = engine.execute(new ExecutionRequest(
                "BTCUSDT",
                SignalDirection.LONG,
                true,
                1.0,
                liquidBook()
        ));

        assertEquals(ExecutionStatus.FILLED, result.status());
        assertEquals(2, result.slices().size());
        assertTrue(result.filledQuantity() > 0);
    }

    @Test
    void rejectsWhenSlippageToleranceIsExceeded() {
        ExecutionProperties properties = new ExecutionProperties();
        properties.setBaseOrderQuantity(1.0);
        properties.setMaxPartialEntries(1);
        properties.setSlippageToleranceBps(0.1);
        properties.setLimitOffsetBps(250.0);

        ExecutionEngineService engine = new ExecutionEngineService(properties);
        ExecutionResult result = engine.execute(new ExecutionRequest(
                "BTCUSDT",
                SignalDirection.LONG,
                true,
                1.0,
                wideSpreadBook()
        ));

        assertEquals(ExecutionStatus.REJECTED, result.status());
        assertTrue(result.notes().toLowerCase().contains("slippage"));
    }

    @Test
    void supportsPartialEntriesWhenBookDepthIsInsufficient() {
        ExecutionProperties properties = new ExecutionProperties();
        properties.setBaseOrderQuantity(3.0);
        properties.setMaxPartialEntries(3);
        properties.setSlippageToleranceBps(100.0);
        properties.setLimitOffsetBps(1.0);

        ExecutionEngineService engine = new ExecutionEngineService(properties);
        ExecutionResult result = engine.execute(new ExecutionRequest(
                "BTCUSDT",
                SignalDirection.LONG,
                true,
                1.0,
                shallowBook()
        ));

        assertEquals(ExecutionStatus.PARTIAL, result.status());
        assertEquals(3, result.partialEntries());
        assertTrue(result.filledQuantity() < result.requestedQuantity());
    }

    @Test
    void supportsLunoExecutionInFuturesModeWhenAdapterProvidesFills() {
        ExecutionProperties properties = new ExecutionProperties();
        properties.setProvider("luno");
        properties.setMarketType("FUTURES");
        properties.setLiveEnabled(true);
        properties.setBaseOrderQuantity(1.0);
        properties.setMaxPartialEntries(1);
        properties.setSlippageToleranceBps(50.0);
        properties.setLimitOffsetBps(1.0);

        LunoExecutionAdapter adapter = Mockito.mock(LunoExecutionAdapter.class);
        Mockito.when(adapter.submitMarketOrder("BTCUSDT", SignalDirection.LONG, 1.0, "FUTURES"))
                .thenReturn("ORD-1");
        Mockito.when(adapter.pollUntilTerminal("ORD-1", "FUTURES"))
                .thenReturn(new OrderStatusResult("ORD-1", OrderState.COMPLETE, 1.0, 65001.0, "COMPLETE"));

        ExecutionEngineService engine = new ExecutionEngineService(properties, adapter, null);
        ExecutionResult result = engine.execute(new ExecutionRequest(
                "BTCUSDT",
                SignalDirection.LONG,
                true,
                1.0,
                liquidBook()
        ));

        assertEquals(ExecutionStatus.FILLED, result.status());
        assertTrue(result.notes().toLowerCase().contains("markettype=futures"));
    }

    @Test
    void attachesProviderAndAccountContextToExecutionResult() {
        ExecutionProperties properties = new ExecutionProperties();
        properties.setProvider("paper");
        properties.setEnabled(true);
        properties.setBaseOrderQuantity(1.0);
        properties.setMaxPartialEntries(1);
        properties.setSlippageToleranceBps(50.0);
        properties.setLimitOffsetBps(2.0);

        ExecutionEngineService engine = new ExecutionEngineService(properties);

        ExecutionResult filled = engine.execute(new ExecutionRequest(
                "BTCUSDT",
                SignalDirection.LONG,
                true,
                1.0,
                liquidBook(),
                "PAPER",
                "PAPER-001"
        ));
        assertEquals("paper", filled.provider());
        assertEquals("PAPER", filled.accountType());
        assertEquals("PAPER-001", filled.accountCode());

        ExecutionResult notSent = engine.execute(new ExecutionRequest(
                "BTCUSDT",
                SignalDirection.LONG,
                false,
                1.0,
                liquidBook(),
                "PAPER",
                "PAPER-001"
        ));
        assertEquals(ExecutionStatus.NOT_SENT, notSent.status());
        assertEquals("paper", notSent.provider());
        assertEquals("PAPER", notSent.accountType());
        assertEquals("PAPER-001", notSent.accountCode());
    }

    private OrderBookSnapshot liquidBook() {
        return new OrderBookSnapshot(
                "BTCUSDT",
                Instant.now(),
                List.of(new OrderBookLevel(65000.0, 10.0)),
                List.of(new OrderBookLevel(65001.0, 10.0), new OrderBookLevel(65002.0, 10.0))
        );
    }

    private OrderBookSnapshot wideSpreadBook() {
        return new OrderBookSnapshot(
                "BTCUSDT",
                Instant.now(),
                List.of(new OrderBookLevel(64000.0, 5.0)),
                List.of(new OrderBookLevel(66000.0, 5.0))
        );
    }

    private OrderBookSnapshot shallowBook() {
        return new OrderBookSnapshot(
                "BTCUSDT",
                Instant.now(),
                List.of(new OrderBookLevel(65000.0, 1.0)),
                List.of(
                        new OrderBookLevel(65001.0, 0.3),
                        new OrderBookLevel(65002.0, 0.2)
                )
        );
    }
}




