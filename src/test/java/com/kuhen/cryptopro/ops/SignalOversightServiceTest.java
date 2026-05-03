package com.kuhen.cryptopro.ops;

import com.kuhen.cryptopro.ai.MarketRegime;
import com.kuhen.cryptopro.strategy.SignalDirection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SignalOversightServiceTest {

    @Test
    void suppressesDbSignalWhenTelemetryOutcomeMatchesLongShortToBuySellAtSameMinute() throws Exception {
        SignalTelemetryService telemetryService = new SignalTelemetryService();
        Instant ts = Instant.parse("2026-04-29T00:10:15Z");
        telemetryService.record(new SignalLogEntry(
                ts,
                "BTCUSDT",
                SignalDirection.LONG,
                0.82,
                0.79,
                false,
                true,
                false,
                MarketRegime.TRENDING,
                0.91,
                65000.0,
                64500.0,
                66000.0,
                SignalOutcome.SKIPPED,
                "Signal not tradable"
        ));

        SignalOversightService service = new SignalOversightService(telemetryService);
        EntityManager entityManager = Mockito.mock(EntityManager.class);
        Query query = Mockito.mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(Mockito.eq("cutoff"), Mockito.any())).thenReturn(query);
        when(query.setParameter(Mockito.eq("maxRows"), Mockito.any())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(new Object[]{
                1L,
                1L,
                "M15",
                "BUY",
                0.82,
                Timestamp.from(ts),
                "BTCUSDT",
                65000.0,
                64500.0,
                66000.0,
                null
        }));

        Field entityManagerField = SignalOversightService.class.getDeclaredField("entityManager");
        entityManagerField.setAccessible(true);
        entityManagerField.set(service, entityManager);

        SignalOversightResponse response = service.buildOversightReport("BTCUSDT", 2, 20, 60);

        assertEquals(0, response.openSignals().size(), "matched skipped signal should not remain open from DB row");
        assertEquals(1, response.missedTrades().size(), "matched skipped signal should appear once as missed");
        assertEquals("BTCUSDT", response.missedTrades().getFirst().symbol());
    }
}


