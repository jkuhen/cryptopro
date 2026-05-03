package com.kuhen.cryptopro.ops;

import com.kuhen.cryptopro.config.PaperPortfolioProperties;
import com.kuhen.cryptopro.execution.ExecutionResult;
import com.kuhen.cryptopro.execution.ExecutionStatus;
import com.kuhen.cryptopro.strategy.SignalDirection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperPortfolioServiceTest {

    @Test
    void updatesBalancesPositionsAndEquityCurveOnExecution() {
        PaperPortfolioProperties properties = new PaperPortfolioProperties();
        properties.setInitialCashUsd(10000.0);
        properties.setFeeRate(0.001);
        properties.setMaxEquityPoints(50);

        PaperPortfolioService service = new PaperPortfolioService(properties);

        ExecutionResult execution = new ExecutionResult(
                ExecutionStatus.FILLED,
                1.0,
                1.0,
                100.0,
                100.0,
                0.5,
                1,
                List.of(),
                "Filled"
        );

        service.applyExecution("BTCUSDT", SignalDirection.LONG, execution, 102.0);
        PaperPortfolioSnapshot snapshot = service.snapshot(102.0, "BTCUSDT");

        assertTrue(snapshot.cashBalance() < snapshot.initialCash());
        assertTrue(snapshot.positions().stream().anyMatch(p -> p.symbol().equals("BTCUSDT") && p.quantity() > 0));
        assertTrue(snapshot.totalFees() > 0.0);
        assertTrue(!snapshot.equityCurve().isEmpty());
    }

    @Test
    void resetPortfolioRestoresInitialState() {
        PaperPortfolioProperties properties = new PaperPortfolioProperties();
        properties.setInitialCashUsd(5000.0);
        properties.setFeeRate(0.001);
        properties.setMaxEquityPoints(50);

        PaperPortfolioService service = new PaperPortfolioService(properties);
        service.applyExecution("BTCUSDT", SignalDirection.LONG, new ExecutionResult(
                ExecutionStatus.FILLED,
                1.0,
                1.0,
                100.0,
                100.0,
                0.2,
                1,
                List.of(),
                "Filled"
        ), 101.0);

        PaperPortfolioSnapshot reset = service.resetPortfolio();

        assertEquals(5000.0, reset.cashBalance());
        assertEquals(5000.0, reset.equity());
        assertEquals(0.0, reset.totalFees());
        assertEquals(0.0, reset.totalRealizedPnl());
        assertTrue(reset.positions().isEmpty());
        assertTrue(!reset.equityCurve().isEmpty());
    }
}

