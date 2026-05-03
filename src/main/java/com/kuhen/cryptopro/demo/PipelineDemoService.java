package com.kuhen.cryptopro.demo;

import com.kuhen.cryptopro.ai.RegimeDetectionModel;
import com.kuhen.cryptopro.ai.RegimeDetectionResult;
import com.kuhen.cryptopro.ai.RegimeFeatureEngineeringService;
import com.kuhen.cryptopro.ai.RegimeClassificationFeatures;
import com.kuhen.cryptopro.ai.SignalScoringFeatures;
import com.kuhen.cryptopro.ai.SignalScoringResult;
import com.kuhen.cryptopro.ai.StrategyAdaptationResult;
import com.kuhen.cryptopro.ai.StrategyAdaptationService;
import com.kuhen.cryptopro.ai.TradeSignalPredictionService;
import com.kuhen.cryptopro.data.FeatureEngineeringUtil;
import com.kuhen.cryptopro.data.MarketDataProvider;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.FeedStatus;
import com.kuhen.cryptopro.data.model.FundingRate;
import com.kuhen.cryptopro.data.model.LiquidationEvent;
import com.kuhen.cryptopro.data.model.OpenInterestSnapshot;
import com.kuhen.cryptopro.data.model.OrderBookSnapshot;
import com.kuhen.cryptopro.data.model.Timeframe;
import com.kuhen.cryptopro.preprocess.PreprocessingResult;
import com.kuhen.cryptopro.preprocess.PreprocessingService;
import com.kuhen.cryptopro.execution.ExecutionEngineService;
import com.kuhen.cryptopro.execution.ExecutionRequest;
import com.kuhen.cryptopro.execution.ExecutionResult;
import com.kuhen.cryptopro.execution.ExecutionStatus;
import com.kuhen.cryptopro.execution.RiskManagedExecutionResult;
import com.kuhen.cryptopro.ops.SignalLogEntry;
import com.kuhen.cryptopro.ops.SignalOutcome;
import com.kuhen.cryptopro.ops.SignalTelemetryService;
import com.kuhen.cryptopro.risk.RiskManagementRequest;
import com.kuhen.cryptopro.risk.RiskManagementResult;
import com.kuhen.cryptopro.strategy.DerivativesSignal;
import com.kuhen.cryptopro.strategy.DerivativesSignalFusionService;
import com.kuhen.cryptopro.strategy.SmcDetector;
import com.kuhen.cryptopro.strategy.SmcSignal;
import com.kuhen.cryptopro.strategy.StrategyDecision;
import com.kuhen.cryptopro.strategy.StrategyEngine;
import com.kuhen.cryptopro.strategy.WeightedBiasResult;
import com.kuhen.cryptopro.risk.RiskGateResult;
import com.kuhen.cryptopro.risk.RiskGateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class PipelineDemoService {

    private final MarketDataProvider marketDataProvider;
    private final PreprocessingService preprocessingService;
    private final StrategyEngine strategyEngine;
    private final SmcDetector smcDetector;
    private final DerivativesSignalFusionService derivativesSignalFusionService;
    private final RiskGateService riskGateService;
    private final TradeSignalPredictionService tradeSignalPredictionService;
    private final RegimeDetectionModel regimeDetectionModel;
    private final RegimeFeatureEngineeringService regimeFeatureEngineeringService;
    private final StrategyAdaptationService strategyAdaptationService;
    private final ExecutionEngineService executionEngineService;
    private final SignalTelemetryService signalTelemetryService;

    /** Default constructor for framework instantiation. */
    public PipelineDemoService() {
        this.marketDataProvider = null;
        this.preprocessingService = null;
        this.strategyEngine = null;
        this.smcDetector = null;
        this.derivativesSignalFusionService = null;
        this.riskGateService = null;
        this.tradeSignalPredictionService = null;
        this.regimeDetectionModel = null;
        this.regimeFeatureEngineeringService = null;
        this.strategyAdaptationService = null;
        this.executionEngineService = null;
        this.signalTelemetryService = null;
    }

    @Autowired
    public PipelineDemoService(
            MarketDataProvider marketDataProvider,
            PreprocessingService preprocessingService,
            StrategyEngine strategyEngine,
            SmcDetector smcDetector,
            DerivativesSignalFusionService derivativesSignalFusionService,
            RiskGateService riskGateService,
            TradeSignalPredictionService tradeSignalPredictionService,
            RegimeDetectionModel regimeDetectionModel,
            RegimeFeatureEngineeringService regimeFeatureEngineeringService,
            StrategyAdaptationService strategyAdaptationService,
            ExecutionEngineService executionEngineService,
            SignalTelemetryService signalTelemetryService
    ) {
        this.marketDataProvider = marketDataProvider;
        this.preprocessingService = preprocessingService;
        this.strategyEngine = strategyEngine;
        this.smcDetector = smcDetector;
        this.derivativesSignalFusionService = derivativesSignalFusionService;
        this.riskGateService = riskGateService;
        this.tradeSignalPredictionService = tradeSignalPredictionService;
        this.regimeDetectionModel = regimeDetectionModel;
        this.regimeFeatureEngineeringService = regimeFeatureEngineeringService;
        this.strategyAdaptationService = strategyAdaptationService;
        this.executionEngineService = executionEngineService;
        this.signalTelemetryService = signalTelemetryService;
    }

    public PipelineDemoResponse run(String symbol) {
        List<Candle> h1Candles = marketDataProvider.getRecentCandles(symbol, Timeframe.H1, 48);
        List<Candle> m15Candles = marketDataProvider.getRecentCandles(symbol, Timeframe.M15, 64);
        // Use M1 candles for paper-trading responsiveness while keeping H1/M15 context.
        List<Candle> m5Candles = marketDataProvider.getRecentCandles(symbol, Timeframe.M1, 120);

        PreprocessingResult h1 = preprocessingService.preprocess(h1Candles);
        PreprocessingResult m15 = preprocessingService.preprocess(m15Candles);
        PreprocessingResult m5 = preprocessingService.preprocess(m5Candles);

        WeightedBiasResult bias = strategyEngine.weightedBias(h1, m15, m5);
        SmcSignal smcSignal = smcDetector.detect(m5.candles());

        OrderBookSnapshot orderBook = marketDataProvider.getLatestOrderBook(symbol);
        FeedStatus feedStatus = marketDataProvider.getFeedStatus(symbol);
        RiskGateResult riskGateResult = riskGateService.evaluate(orderBook, feedStatus);

        FundingRate fundingRate = marketDataProvider.getLatestFundingRate(symbol);
        List<OpenInterestSnapshot> openInterest = marketDataProvider.getRecentOpenInterest(symbol, 12);
        List<LiquidationEvent> liquidations = marketDataProvider.getRecentLiquidations(symbol, 20);
        DerivativesSignal derivativesSignal = derivativesSignalFusionService.evaluate(m5Candles, openInterest, fundingRate, liquidations);

        SignalScoringFeatures aiFeatures = buildAiFeatures(bias, m5, derivativesSignal);
        RegimeClassificationFeatures regimeFeatures = regimeFeatureEngineeringService.build(
                h1Candles,
                m15Candles,
                m5Candles,
                aiFeatures.volumeSpike(),
                aiFeatures.openInterestChange(),
                aiFeatures.fundingRate()
        );
        RegimeDetectionResult regimeDetection = regimeDetectionModel.detect(regimeFeatures);
        SignalScoringResult scoringResult = tradeSignalPredictionService.scoreAndPersist(symbol, null, aiFeatures, Instant.now());

        double qualityScore = (h1.dataQualityScore() + m15.dataQualityScore() + m5.dataQualityScore()) / 3.0;
        StrategyDecision baseDecision = strategyEngine.combine(bias, smcSignal, derivativesSignal, qualityScore, regimeDetection.regime());
        StrategyAdaptationResult adaptation = strategyAdaptationService.adapt(baseDecision, scoringResult, regimeDetection);

        RiskManagedExecutionResult riskManagedExecution = executionEngineService.executeWithRisk(new ExecutionRequest(
                symbol,
                baseDecision.direction(),
                adaptation.tradable() && riskGateResult.passed(),
                adaptation.sizeMultiplier(),
                orderBook
        ), m15Candles, midpoint(orderBook));

        RiskManagementResult riskManagement = riskManagedExecution.risk();
        ExecutionResult executionResult = riskManagedExecution.execution();
        boolean tradable = riskManagement.allowed() && riskGateResult.passed();
        double sizeMultiplier = tradable ? riskManagement.adjustedSizeMultiplier() : 0.0;
        Double projectedEntry = midpoint(orderBook);
        Double projectedStopLoss = riskManagement.stopLossPrice() > 0.0 ? riskManagement.stopLossPrice() : null;
        Double projectedTakeProfit = deriveTakeProfit(baseDecision.direction(), projectedEntry, projectedStopLoss);

        signalTelemetryService.record(new SignalLogEntry(
                Instant.now(),
                symbol,
                baseDecision.direction(),
                baseDecision.finalScore(),
                scoringResult.probabilityOfSuccess(),
                smcSignal.liquiditySweep(),
                aiFeatures.volumeSpike() > 1.5,
                derivativesSignal.direction() == baseDecision.direction() && derivativesSignal.direction() != com.kuhen.cryptopro.strategy.SignalDirection.NEUTRAL,
                regimeDetection.regime(),
                regimeDetection.confidence(),
                projectedEntry,
                projectedStopLoss,
                projectedTakeProfit,
                resolveOutcome(executionResult, baseDecision),
                executionResult.notes()
        ));

        String rationale = baseDecision.rationale()
                + " | " + adaptation.notes()
                + (riskManagement.reasons().isEmpty() ? "" : " | risk=" + String.join("; ", riskManagement.reasons()))
                + (!riskGateResult.passed() ? " (risk blocked: " + String.join("; ", riskGateResult.reasons()) + ")" : "");

        return new PipelineDemoResponse(
                symbol,
                m5.sessionType(),
                m5.volatilityRegime(),
                qualityScore,
                orderBook.spread(),
                bias.score(),
                smcSignal.score(),
                derivativesSignal.score(),
                baseDecision.finalScore(),
                baseDecision.direction(),
                sizeMultiplier,
                tradable,
                riskGateResult.passed(),
                riskGateResult.reasons(),
                riskGateResult.latencyMs(),
                riskGateResult.feedAgeSeconds(),
                riskGateResult.spreadBps(),
                scoringResult.probabilityOfSuccess(),
                scoringResult.confidence(),
                regimeDetection.regime(),
                regimeDetection.confidence(),
                scoringResult.modelName(),
                scoringResult.modelVersion(),
                scoringResult.notes(),
                riskManagement.allowed(),
                riskManagement.adjustedSizeMultiplier(),
                riskManagement.atr(),
                riskManagement.stopLossPrice(),
                riskManagement.riskPerTradePercent(),
                riskManagement.dailyLossPercent(),
                riskManagement.dailyLossCapPercent(),
                riskManagement.concurrentTrades(),
                riskManagement.maxConcurrentTrades(),
                riskManagement.maxAllowedQuantity(),
                executionResult.status(),
                executionResult.requestedQuantity(),
                executionResult.filledQuantity(),
                executionResult.limitPrice(),
                executionResult.averageFillPrice(),
                executionResult.slippageBps(),
                executionResult.partialEntries(),
                executionResult.slices(),
                executionResult.notes(),
                rationale
        );
    }

    private double midpoint(OrderBookSnapshot orderBook) {
        if (orderBook.bids().isEmpty() || orderBook.asks().isEmpty()) {
            return 0.0;
        }
        return (orderBook.bids().get(0).price() + orderBook.asks().get(0).price()) / 2.0;
    }

    private SignalScoringFeatures buildAiFeatures(
            WeightedBiasResult bias,
            PreprocessingResult m5,
            DerivativesSignal derivativesSignal
    ) {
        double volumeSpike = m5.candles().stream().mapToDouble(c -> Math.max(0.0, c.volumeZScore())).max().orElse(0.0);
        double oiChangePercent = derivativesSignal.openInterestDelta() * 100.0;
        java.util.List<Double> closes = m5.candles().stream().map(com.kuhen.cryptopro.preprocess.PreprocessedCandle::cleanClose).toList();
        double rsi = java.util.Optional.ofNullable(FeatureEngineeringUtil.calculateRsi(closes, 14)).orElse(50.0);
        return new SignalScoringFeatures(
                bias.score(),
                rsi,
                volumeSpike,
                oiChangePercent,
                derivativesSignal.fundingRate()
        );
    }

    private SignalOutcome resolveOutcome(ExecutionResult executionResult, StrategyDecision decision) {
        boolean executed = executionResult.status() == ExecutionStatus.FILLED || executionResult.status() == ExecutionStatus.PARTIAL;
        if (!executed) {
            return SignalOutcome.SKIPPED;
        }

        if (decision.direction() == com.kuhen.cryptopro.strategy.SignalDirection.LONG && decision.finalScore() > 0) {
            return SignalOutcome.WIN;
        }
        if (decision.direction() == com.kuhen.cryptopro.strategy.SignalDirection.SHORT && decision.finalScore() < 0) {
            return SignalOutcome.WIN;
        }
        return SignalOutcome.LOSS;
    }

    private Double deriveTakeProfit(com.kuhen.cryptopro.strategy.SignalDirection direction,
                                    Double entryPrice,
                                    Double stopLoss) {
        if (entryPrice == null || stopLoss == null || direction == com.kuhen.cryptopro.strategy.SignalDirection.NEUTRAL) {
            return null;
        }
        double riskDistance = Math.abs(entryPrice - stopLoss);
        if (riskDistance <= 0.0) {
            return null;
        }
        double rewardDistance = riskDistance * 1.5;
        return direction == com.kuhen.cryptopro.strategy.SignalDirection.LONG
                ? entryPrice + rewardDistance
                : entryPrice - rewardDistance;
    }
}

