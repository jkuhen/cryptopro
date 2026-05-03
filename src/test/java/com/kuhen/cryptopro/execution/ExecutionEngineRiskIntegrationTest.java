package com.kuhen.cryptopro.execution;

import com.kuhen.cryptopro.config.ExecutionProperties;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.OrderBookLevel;
import com.kuhen.cryptopro.data.model.OrderBookSnapshot;
import com.kuhen.cryptopro.data.model.Timeframe;
import com.kuhen.cryptopro.risk.RiskManagementResult;
import com.kuhen.cryptopro.risk.RiskManagementService;
import com.kuhen.cryptopro.strategy.SignalDirection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionEngineRiskIntegrationTest {

    @Test
    void blocksExecutionWhenRiskServiceRejectsTrade() {
        ExecutionProperties properties = new ExecutionProperties();
        properties.setProvider("paper");
        properties.setEnabled(true);

        RiskManagementService riskService = mock(RiskManagementService.class);
        when(riskService.evaluate(any())).thenReturn(new RiskManagementResult(
                false,
                List.of("Daily loss cap reached"),
                0.0,
                1.0,
                64000.0,
                1.0,
                5.0,
                5.0,
                3,
                3,
                0.0
        ));

        ExecutionEngineService engine = new ExecutionEngineService(properties, null, riskService);

        RiskManagedExecutionResult result = engine.executeWithRisk(
                new ExecutionRequest("BTCUSDT", SignalDirection.LONG, true, 1.0, liquidBook()),
                atrCandles(),
                65000.0
        );

        assertEquals(false, result.risk().allowed());
        assertEquals(ExecutionStatus.NOT_SENT, result.execution().status());
        verify(riskService).recordExecution(any());
    }

    @Test
    void appliesRiskAdjustedSizeBeforeExecution() {
        ExecutionProperties properties = new ExecutionProperties();
        properties.setProvider("paper");
        properties.setEnabled(true);
        properties.setBaseOrderQuantity(2.0);
        properties.setMaxPartialEntries(1);

        RiskManagementService riskService = mock(RiskManagementService.class);
        when(riskService.evaluate(any())).thenReturn(new RiskManagementResult(
                true,
                List.of("Position size reduced to respect max risk per trade"),
                0.5,
                1.0,
                64000.0,
                1.0,
                0.5,
                5.0,
                1,
                3,
                1.0
        ));

        ExecutionEngineService engine = new ExecutionEngineService(properties, null, riskService);

        RiskManagedExecutionResult result = engine.executeWithRisk(
                new ExecutionRequest("BTCUSDT", SignalDirection.LONG, true, 1.0, liquidBook()),
                atrCandles(),
                65000.0
        );

        assertEquals(1.0, result.execution().requestedQuantity());
        verify(riskService).recordExecution(any());
    }

    private OrderBookSnapshot liquidBook() {
        return new OrderBookSnapshot(
                "BTCUSDT",
                Instant.now(),
                List.of(new OrderBookLevel(65000.0, 10.0)),
                List.of(new OrderBookLevel(65001.0, 10.0), new OrderBookLevel(65002.0, 10.0))
        );
    }

    private List<Candle> atrCandles() {
        return List.of(
                new Candle("BTCUSDT", Timeframe.M15, Instant.now().minusSeconds(900), 100, 101, 99, 100.5, 10),
                new Candle("BTCUSDT", Timeframe.M15, Instant.now().minusSeconds(600), 100.5, 102, 100, 101.8, 11),
                new Candle("BTCUSDT", Timeframe.M15, Instant.now().minusSeconds(300), 101.8, 103, 101, 102.6, 12),
                new Candle("BTCUSDT", Timeframe.M15, Instant.now(), 102.6, 104, 102, 103.4, 13)
        );
    }
}

