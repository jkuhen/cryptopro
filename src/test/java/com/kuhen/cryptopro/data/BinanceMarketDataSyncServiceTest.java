package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.config.BinanceProperties;
import com.kuhen.cryptopro.data.dto.BinanceMarketSyncResultDto;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.FundingRate;
import com.kuhen.cryptopro.data.model.OpenInterestSnapshot;
import com.kuhen.cryptopro.data.model.Timeframe;
import com.kuhen.cryptopro.strategy.SignalGenerationService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BinanceMarketDataSyncServiceTest {

    @Test
    void syncSymbolPersistsClosedM1AndAggregatedHigherTimeframes() {
        BinanceMarketDataProvider provider = mock(BinanceMarketDataProvider.class);
        CandlePersistenceService persistenceService = mock(CandlePersistenceService.class);
        DerivativesDataPersistenceService derivativesDataPersistenceService = mock(DerivativesDataPersistenceService.class);
        BinanceProperties properties = properties();
        Clock clock = Clock.fixed(Instant.parse("2026-04-25T12:00:00Z"), ZoneOffset.UTC);

        when(provider.getRecentCandlesFromExchange(eq("BTCUSDT"), eq(Timeframe.M1), eq(180)))
                .thenReturn(buildHourlyM1Series());
        when(provider.getLatestFundingRate("BTCUSDT"))
                .thenReturn(new FundingRate("BTCUSDT", Instant.parse("2026-04-25T08:00:00Z"), 0.0001));
        when(provider.getRecentOpenInterest("BTCUSDT", 1))
                .thenReturn(List.of(new OpenInterestSnapshot("BTCUSDT", Instant.parse("2026-04-25T11:55:00Z"), 1_000_000_000.0)));
        when(persistenceService.saveClosedCandles(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(derivativesDataPersistenceService.persistSnapshot(eq("BTCUSDT"), eq(Instant.parse("2026-04-25T12:00:00Z")), eq(new OpenInterestSnapshot("BTCUSDT", Instant.parse("2026-04-25T11:55:00Z"), 1_000_000_000.0)), eq(new FundingRate("BTCUSDT", Instant.parse("2026-04-25T08:00:00Z"), 0.0001))))
                .thenReturn(true);

        BinanceMarketDataSyncService service = new BinanceMarketDataSyncService(
                provider, persistenceService, derivativesDataPersistenceService,
                mock(FeatureEngineeringService.class), mock(SignalGenerationService.class),
                properties, clock);
        BinanceMarketSyncResultDto result = service.syncSymbol("btcusdt");

        assertEquals("BTCUSDT", result.symbol());
        assertEquals(60, result.persistedM1());
        assertEquals(12, result.persistedM5());
        assertEquals(4, result.persistedM15());
        assertEquals(1, result.persistedH1());
        assertTrue(result.notes().contains("derivatives_data"));
        verify(provider).getRecentCandlesFromExchange("BTCUSDT", Timeframe.M1, 180);
        verify(derivativesDataPersistenceService, times(1)).persistSnapshot(
                eq("BTCUSDT"),
                eq(Instant.parse("2026-04-25T12:00:00Z")),
                eq(new OpenInterestSnapshot("BTCUSDT", Instant.parse("2026-04-25T11:55:00Z"), 1_000_000_000.0)),
                eq(new FundingRate("BTCUSDT", Instant.parse("2026-04-25T08:00:00Z"), 0.0001))
        );
    }

    @Test
    void aggregateClosedCandlesSkipsIncompleteWindows() {
        BinanceMarketDataProvider provider = mock(BinanceMarketDataProvider.class);
        CandlePersistenceService persistenceService = mock(CandlePersistenceService.class);
        DerivativesDataPersistenceService derivativesDataPersistenceService = mock(DerivativesDataPersistenceService.class);
        BinanceMarketDataSyncService service = new BinanceMarketDataSyncService(
                provider,
                persistenceService,
                derivativesDataPersistenceService,
                mock(FeatureEngineeringService.class),
                mock(SignalGenerationService.class),
                properties(),
                Clock.fixed(Instant.parse("2026-04-25T12:00:00Z"), ZoneOffset.UTC)
        );

        List<Candle> incompleteWindow = List.of(
                candle("BTCUSDT", Instant.parse("2026-04-25T11:00:00Z"), 100, 101, 99, 100.5, 10),
                candle("BTCUSDT", Instant.parse("2026-04-25T11:01:00Z"), 100.5, 101.5, 100, 101, 11),
                candle("BTCUSDT", Instant.parse("2026-04-25T11:03:00Z"), 101, 102, 100.5, 101.8, 12),
                candle("BTCUSDT", Instant.parse("2026-04-25T11:04:00Z"), 101.8, 102.5, 101.2, 102.2, 13)
        );

        List<Candle> aggregated = service.aggregateClosedCandles(
                incompleteWindow,
                Timeframe.M5,
                Instant.parse("2026-04-25T12:00:00Z")
        );

        assertTrue(aggregated.isEmpty());
    }

    @Test
    void syncSymbolContinuesWhenDerivativesApiDataIsMissing() {
        BinanceMarketDataProvider provider = mock(BinanceMarketDataProvider.class);
        CandlePersistenceService persistenceService = mock(CandlePersistenceService.class);
        DerivativesDataPersistenceService derivativesDataPersistenceService = mock(DerivativesDataPersistenceService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-25T12:00:00Z"), ZoneOffset.UTC);

        when(provider.getRecentCandlesFromExchange(eq("BTCUSDT"), eq(Timeframe.M1), eq(180)))
                .thenReturn(buildHourlyM1Series());
        when(provider.getLatestFundingRate("BTCUSDT"))
                .thenReturn(new FundingRate("BTCUSDT", Instant.EPOCH, Double.NaN));
        when(provider.getRecentOpenInterest("BTCUSDT", 1))
                .thenReturn(List.of());
        when(persistenceService.saveClosedCandles(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(derivativesDataPersistenceService.persistSnapshot(eq("BTCUSDT"), eq(Instant.parse("2026-04-25T12:00:00Z")), eq(null), eq(new FundingRate("BTCUSDT", Instant.EPOCH, Double.NaN))))
                .thenReturn(false);

        BinanceMarketDataSyncService service = new BinanceMarketDataSyncService(
                provider,
                persistenceService,
                derivativesDataPersistenceService,
                mock(FeatureEngineeringService.class),
                mock(SignalGenerationService.class),
                properties(),
                clock
        );

        BinanceMarketSyncResultDto result = service.syncSymbol("BTCUSDT");

        assertEquals(60, result.persistedM1());
        assertTrue(result.notes().contains("skipped"));
        verify(derivativesDataPersistenceService).persistSnapshot(
                "BTCUSDT",
                Instant.parse("2026-04-25T12:00:00Z"),
                null,
                new FundingRate("BTCUSDT", Instant.EPOCH, Double.NaN)
        );
    }

    private List<Candle> buildHourlyM1Series() {
        List<Candle> candles = new ArrayList<>();
        Instant start = Instant.parse("2026-04-25T11:00:00Z");
        double price = 100.0;
        for (int i = 0; i < 61; i++) {
            Instant openTime = start.plusSeconds(i * 60L);
            double open = price;
            double close = price + 0.5;
            candles.add(candle("BTCUSDT", openTime, open, close + 0.2, open - 0.2, close, 10 + i));
            price = close;
        }
        return candles;
    }

    private Candle candle(String symbol, Instant openTime, double open, double high, double low, double close, double volume) {
        return new Candle(symbol, Timeframe.M1, openTime, open, high, low, close, volume);
    }

    private BinanceProperties properties() {
        BinanceProperties properties = new BinanceProperties();
        properties.setSyncLookbackCandles(180);
        HashMap<String, String> symbolMap = new HashMap<>();
        symbolMap.put("BTCUSDT", "BTCUSDT");
        properties.setSymbolMap(symbolMap);
        return properties;
    }
}



