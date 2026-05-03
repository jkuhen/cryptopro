package com.kuhen.cryptopro.risk;

import com.kuhen.cryptopro.config.ExecutionProperties;
import com.kuhen.cryptopro.config.RiskProperties;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.Timeframe;
import com.kuhen.cryptopro.execution.ExecutionResult;
import com.kuhen.cryptopro.execution.ExecutionStatus;
import com.kuhen.cryptopro.strategy.SignalDirection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskManagementServiceTest {

    @Test
    void appliesAtrBasedSizingAndStopLoss() {
        RiskProperties riskProperties = new RiskProperties();
        riskProperties.setRiskPerTradePercent(1.0);
        riskProperties.setAccountEquityUsd(1000.0);
        riskProperties.setAtrPeriod(5);
        riskProperties.setAtrStopMultiplier(2.0);

        ExecutionProperties executionProperties = new ExecutionProperties();
        executionProperties.setBaseOrderQuantity(2.0);

        RiskManagementService service = new RiskManagementService(riskProperties, executionProperties, new RiskStateService());

        RiskManagementResult result = service.evaluate(new RiskManagementRequest(
                "BTCUSDT",
                SignalDirection.LONG,
                true,
                1.0,
                100.0,
                candles()
        ));

        assertTrue(result.atr() > 0.0);
        assertTrue(result.stopLossPrice() < 100.0);
        assertTrue(result.maxAllowedQuantity() > 0.0);
    }

    @Test
    void blocksWhenDailyLossCapReached() {
        RiskProperties riskProperties = new RiskProperties();
        riskProperties.setDailyLossCapPercent(0.05);

        ExecutionProperties executionProperties = new ExecutionProperties();
        RiskStateService state = new RiskStateService();
        RiskManagementService service = new RiskManagementService(riskProperties, executionProperties, state);

        state.recordExecution(new ExecutionResult(
                ExecutionStatus.REJECTED,
                1.0,
                1.0,
                1.0,
                1.0,
                10.0,
                1,
                List.of(),
                "Slippage above tolerance"
        ));

        RiskManagementResult result = service.evaluate(new RiskManagementRequest(
                "BTCUSDT",
                SignalDirection.LONG,
                true,
                1.0,
                100.0,
                candles()
        ));

        assertFalse(result.allowed());
        assertTrue(result.reasons().stream().anyMatch(reason -> reason.contains("Daily loss cap")));
    }

    @Test
    void capsConfiguredLimitsAtRequestedHardMaximums() {
        RiskProperties riskProperties = new RiskProperties();
        riskProperties.setRiskPerTradePercent(2.5);
        riskProperties.setDailyLossCapPercent(9.0);
        riskProperties.setMaxConcurrentTrades(8);

        ExecutionProperties executionProperties = new ExecutionProperties();
        executionProperties.setBaseOrderQuantity(1.0);

        RiskManagementService service = new RiskManagementService(riskProperties, executionProperties, new RiskStateService());

        RiskManagementResult result = service.evaluate(new RiskManagementRequest(
                "BTCUSDT",
                SignalDirection.LONG,
                true,
                1.0,
                100.0,
                candles()
        ));

        assertEquals(1.0, result.riskPerTradePercent());
        assertEquals(5.0, result.dailyLossCapPercent());
        assertEquals(3, result.maxConcurrentTrades());
    }

    private List<Candle> candles() {
        return List.of(
                new Candle("BTCUSDT", Timeframe.M15, Instant.now().minusSeconds(900), 100, 101, 99, 100.5, 10),
                new Candle("BTCUSDT", Timeframe.M15, Instant.now().minusSeconds(600), 100.5, 102, 100, 101.8, 11),
                new Candle("BTCUSDT", Timeframe.M15, Instant.now().minusSeconds(300), 101.8, 103, 101, 102.6, 12),
                new Candle("BTCUSDT", Timeframe.M15, Instant.now(), 102.6, 104, 102, 103.4, 13)
        );
    }
}

