package com.kuhen.cryptopro.ops;

import com.kuhen.cryptopro.ai.MarketRegime;
import com.kuhen.cryptopro.strategy.SignalDirection;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignalMonitoringControllerUnitTest {

    @Test
    void latestRegimeEndpointReturnsSymbolRegimeAndConfidence() {
        SignalTelemetryService telemetry = new SignalTelemetryService();
        telemetry.record(new SignalLogEntry(
                Instant.parse("2026-04-26T00:20:00Z"),
                "SOLUSDT",
                SignalDirection.SHORT,
                -0.4,
                0.66,
                false,
                false,
                true,
                MarketRegime.TRENDING,
                0.74,
                null,
                null,
                null,
                SignalOutcome.LOSS,
                "notes"
        ));

        SignalMonitoringController controller = new SignalMonitoringController(telemetry);
        LatestRegimeResponse response = controller.latestRegime("SOLUSDT");

        assertTrue(response.available());
        assertEquals("SOLUSDT", response.symbol());
        assertEquals(MarketRegime.TRENDING, response.regime());
        assertEquals(0.74, response.confidence());
    }
}

