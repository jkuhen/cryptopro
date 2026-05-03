package com.kuhen.cryptopro.orchestrator;

import com.kuhen.cryptopro.ai.SignalScoringModel;
import com.kuhen.cryptopro.ai.SignalScoringResult;
import com.kuhen.cryptopro.config.AiProperties;
import com.kuhen.cryptopro.config.BinanceProperties;
import com.kuhen.cryptopro.data.MarketDataProvider;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.FundingRate;
import com.kuhen.cryptopro.data.model.LiquidationEvent;
import com.kuhen.cryptopro.data.model.LiquidationSide;
import com.kuhen.cryptopro.data.model.OpenInterestSnapshot;
import com.kuhen.cryptopro.data.model.OrderBookLevel;
import com.kuhen.cryptopro.data.model.OrderBookSnapshot;
import com.kuhen.cryptopro.data.model.Timeframe;
import com.kuhen.cryptopro.execution.ExecutionEngineService;
import com.kuhen.cryptopro.execution.ExecutionResult;
import com.kuhen.cryptopro.ops.AccountSelectionService;
import com.kuhen.cryptopro.ops.OpsTelemetryService;
import com.kuhen.cryptopro.ops.PaperPortfolioService;
import com.kuhen.cryptopro.ops.SignalTelemetryService;
import com.kuhen.cryptopro.preprocess.PreprocessedCandle;
import com.kuhen.cryptopro.preprocess.PreprocessingResult;
import com.kuhen.cryptopro.preprocess.PreprocessingService;
import com.kuhen.cryptopro.preprocess.SessionType;
import com.kuhen.cryptopro.preprocess.VolatilityRegime;
import com.kuhen.cryptopro.risk.RiskManagementResult;
import com.kuhen.cryptopro.risk.RiskManagementService;
import com.kuhen.cryptopro.strategy.DerivativesSignal;
import com.kuhen.cryptopro.strategy.DerivativesSignalFusionService;
import com.kuhen.cryptopro.strategy.PriceActionPatternSignal;
import com.kuhen.cryptopro.strategy.SignalDirection;
import com.kuhen.cryptopro.strategy.SmcDetector;
import com.kuhen.cryptopro.strategy.SmcSignal;
import com.kuhen.cryptopro.strategy.StrategyDecision;
import com.kuhen.cryptopro.strategy.StrategyEngine;
import com.kuhen.cryptopro.strategy.WeightedBiasResult;
import com.kuhen.cryptopro.trade.TradeLifecycleManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradingOrchestratorServiceTest {

    @Test
    void runOnceProcessesSymbolsAndContinuesWhenOneFails() {
        MarketDataProvider marketDataProvider = mock(MarketDataProvider.class);
        PreprocessingService preprocessingService = mock(PreprocessingService.class);
        StrategyEngine strategyEngine = mock(StrategyEngine.class);
        SmcDetector smcDetector = mock(SmcDetector.class);
        DerivativesSignalFusionService derivatives = mock(DerivativesSignalFusionService.class);
        SignalScoringModel scoringModel = mock(SignalScoringModel.class);
        RiskManagementService risk = mock(RiskManagementService.class);
        ExecutionEngineService execution = mock(ExecutionEngineService.class);
        SignalTelemetryService signalTelemetryService = mock(SignalTelemetryService.class);
        AccountSelectionService accountSelectionService = mock(AccountSelectionService.class);
        OpsTelemetryService opsTelemetryService = mock(OpsTelemetryService.class);
        PaperPortfolioService paperPortfolioService = mock(PaperPortfolioService.class);
        TradeLifecycleManager tradeLifecycleManager = mock(TradeLifecycleManager.class);

        AiProperties aiProperties = new AiProperties();
        aiProperties.getThresholds().setMinProbability(0.55);
        aiProperties.getThresholds().setMinConfidence(0.25);

        BinanceProperties binance = new BinanceProperties();
        binance.setSymbolMap(Map.of("BTCUSDT", "BTCUSDT", "ETHUSDT", "ETHUSDT"));

        TradingOrchestratorService orchestrator = new TradingOrchestratorService(
                marketDataProvider,
                preprocessingService,
                strategyEngine,
                smcDetector,
                derivatives,
                scoringModel,
                aiProperties,
                risk,
                execution,
                binance,
                signalTelemetryService,
                accountSelectionService,
                opsTelemetryService,
                paperPortfolioService,
                tradeLifecycleManager
        );
        ReflectionTestUtils.setField(orchestrator, "retryAttempts", 2);
        ReflectionTestUtils.setField(orchestrator, "retryBackoffMs", 1L);

        List<Candle> candles = candles("BTCUSDT", 180);
        OrderBookSnapshot orderBook = new OrderBookSnapshot(
                "BTCUSDT",
                Instant.now(),
                List.of(new OrderBookLevel(65000.0, 10.0)),
                List.of(new OrderBookLevel(65001.0, 10.0))
        );

        when(marketDataProvider.getRecentCandles(eq("BTCUSDT"), any(Timeframe.class), any(Integer.class))).thenReturn(candles);
        when(marketDataProvider.getRecentCandles(eq("ETHUSDT"), any(Timeframe.class), any(Integer.class)))
                .thenThrow(new RuntimeException("data error"));
        when(marketDataProvider.getLatestOrderBook("BTCUSDT")).thenReturn(orderBook);
        when(marketDataProvider.getLatestFundingRate("BTCUSDT")).thenReturn(new FundingRate("BTCUSDT", Instant.now(), 0.001));
        when(marketDataProvider.getRecentOpenInterest("BTCUSDT", 24))
                .thenReturn(List.of(new OpenInterestSnapshot("BTCUSDT", Instant.now().minusSeconds(60), 1_000_000), new OpenInterestSnapshot("BTCUSDT", Instant.now(), 1_050_000)));
        when(marketDataProvider.getRecentLiquidations("BTCUSDT", 50))
                .thenReturn(List.of(new LiquidationEvent("BTCUSDT", Instant.now(), LiquidationSide.LONG, 65000, 10_000)));

        PreprocessingResult preprocessingResult = new PreprocessingResult(
                List.of(new PreprocessedCandle(candles.get(0), candles.get(0).close(), 0.1, 1.0)),
                SessionType.ASIA,
                VolatilityRegime.NORMAL,
                0.9
        );
        when(preprocessingService.preprocess(any())).thenReturn(preprocessingResult);

        when(strategyEngine.weightedBias(any(), any(), any()))
                .thenReturn(new WeightedBiasResult(0.25, SignalDirection.LONG, 0.1, true, 1.0));
        when(smcDetector.detect(any())).thenReturn(new SmcSignal(
                PriceActionPatternSignal.none("none"),
                PriceActionPatternSignal.none("none"),
                PriceActionPatternSignal.none("none"),
                0.0,
                SignalDirection.NEUTRAL,
                "none"
        ));
        when(derivatives.evaluate(any(), any(), any(), any()))
                .thenReturn(new DerivativesSignal(SignalDirection.LONG, 0.2, 0.1, 0.001, 0.02, "ok"));
        when(strategyEngine.combine(any(), any(), any(), any(Double.class)))
                .thenReturn(new StrategyDecision(0.3, SignalDirection.LONG, 1.0, true, "ok"));

        when(scoringModel.score(any())).thenReturn(new SignalScoringResult(0.8, 0.9, "mock", "v1", "ok"));
        when(accountSelectionService.resolveActiveExecutionAccount()).thenReturn(Optional.empty());
        when(risk.evaluate(any())).thenReturn(new RiskManagementResult(
                true,
                List.of(),
                1.0,
                100.0,
                64900.0,
                1.0,
                0.0,
                5.0,
                0,
                3,
                1.0
        ));
        when(execution.execute(any())).thenReturn(ExecutionResult.notSent("paper"));

        TradingOrchestratorService.CycleResult result = orchestrator.runOnce();

        assertEquals(2, result.processedSymbols());
        assertEquals(1, result.successfulSymbols());
        assertEquals(1, result.failedSymbols());
        verify(execution, times(1)).execute(any());
        verify(risk, times(1)).recordExecution(any());
        verify(signalTelemetryService, times(1)).record(any());
    }

    private List<Candle> candles(String symbol, int limit) {
        Instant now = Instant.now();
        return java.util.stream.IntStream.range(0, limit)
                .mapToObj(i -> {
                    double px = 65000.0 + i;
                    return new Candle(symbol, Timeframe.M5, now.minusSeconds((long) (limit - i) * 300L), px - 5, px + 10, px - 10, px, 100 + i);
                })
                .toList();
    }
}

