package com.kuhen.cryptopro.ops;

import com.kuhen.cryptopro.ai.MarketRegime;
import com.kuhen.cryptopro.strategy.SignalDirection;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignalTelemetryServiceTest {

    @Test
    void latestRegimeForSymbolReturnsMostRecentEntry() {
        SignalTelemetryService service = new SignalTelemetryService();
        service.record(new SignalLogEntry(
                Instant.parse("2026-04-26T00:00:00Z"),
                "BTCUSDT",
                SignalDirection.LONG,
                0.5,
                0.7,
                false,
                true,
                true,
                MarketRegime.RANGING,
                0.42,
                null,
                null,
                null,
                SignalOutcome.WIN,
                "older"
        ));
        service.record(new SignalLogEntry(
                Instant.parse("2026-04-26T00:05:00Z"),
                "BTCUSDT",
                SignalDirection.LONG,
                0.6,
                0.8,
                true,
                true,
                true,
                MarketRegime.HIGH_VOLATILITY,
                0.88,
                null,
                null,
                null,
                SignalOutcome.WIN,
                "latest"
        ));

        LatestRegimeResponse response = service.latestRegimeForSymbol("btcusdt");

        assertTrue(response.available());
        assertEquals("BTCUSDT", response.symbol());
        assertEquals(MarketRegime.HIGH_VOLATILITY, response.regime());
        assertEquals(0.88, response.confidence());
        assertEquals(Instant.parse("2026-04-26T00:05:00Z"), response.timestamp());
    }

    @Test
    void latestRegimeForSymbolReturnsUnavailableWhenNoEntryExists() {
        SignalTelemetryService service = new SignalTelemetryService();

        LatestRegimeResponse response = service.latestRegimeForSymbol("ETHUSDT");

        assertFalse(response.available());
        assertEquals("ETHUSDT", response.symbol());
        assertEquals(MarketRegime.RANGING, response.regime());
        assertEquals(0.0, response.confidence());
    }

    @Test
    void buildOversightReportIncludesConfidenceForOpenSignals() {
        SignalTelemetryService service = new SignalTelemetryService();
        service.record(new SignalLogEntry(
                Instant.now().minusSeconds(10 * 60),
                "BTCUSDT",
                SignalDirection.SHORT,
                0.21,
                0.57,
                true,
                true,
                false,
                MarketRegime.TRENDING,
                0.91,
                69000.0,
                70000.0,
                67000.0,
                null,
                "open"
        ));

        SignalOversightResponse response = service.buildOversightReport("BTCUSDT", 2, 10, 60);

        assertEquals(1, response.openSignals().size());
        SignalOversightResponse.OpenSignalView open = response.openSignals().getFirst();
        assertEquals("BTCUSDT", open.symbol());
        assertEquals("SHORT", open.action());
        assertEquals(0.21, open.confidence());
    }
}

