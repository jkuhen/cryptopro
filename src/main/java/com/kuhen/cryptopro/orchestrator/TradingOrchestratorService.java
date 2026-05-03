package com.kuhen.cryptopro.orchestrator;

import com.kuhen.cryptopro.ai.SignalScoringFeatures;
import com.kuhen.cryptopro.ai.SignalScoringModel;
import com.kuhen.cryptopro.ai.SignalScoringResult;
import com.kuhen.cryptopro.config.AiProperties;
import com.kuhen.cryptopro.config.BinanceProperties;
import com.kuhen.cryptopro.data.FeatureEngineeringUtil;
import com.kuhen.cryptopro.data.MarketDataProvider;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.FundingRate;
import com.kuhen.cryptopro.data.model.LiquidationEvent;
import com.kuhen.cryptopro.data.model.OpenInterestSnapshot;
import com.kuhen.cryptopro.data.model.OrderBookSnapshot;
import com.kuhen.cryptopro.data.model.Timeframe;
import com.kuhen.cryptopro.execution.ExecutionEngineService;
import com.kuhen.cryptopro.execution.ExecutionRequest;
import com.kuhen.cryptopro.execution.ExecutionResult;
import com.kuhen.cryptopro.execution.ExecutionStatus;
import com.kuhen.cryptopro.ops.AccountSelectionService;
import com.kuhen.cryptopro.ops.OpsTelemetryService;
import com.kuhen.cryptopro.ops.PaperPortfolioService;
import com.kuhen.cryptopro.ops.SignalLogEntry;
import com.kuhen.cryptopro.ops.SignalOutcome;
import com.kuhen.cryptopro.ops.SignalTelemetryService;
import com.kuhen.cryptopro.ops.TransactionLogEntry;
import com.kuhen.cryptopro.preprocess.PreprocessingResult;
import com.kuhen.cryptopro.preprocess.PreprocessingService;
import com.kuhen.cryptopro.risk.RiskManagementRequest;
import com.kuhen.cryptopro.risk.RiskManagementResult;
import com.kuhen.cryptopro.risk.RiskManagementService;
import com.kuhen.cryptopro.strategy.DerivativesSignal;
import com.kuhen.cryptopro.strategy.DerivativesSignalFusionService;
import com.kuhen.cryptopro.strategy.SmcDetector;
import com.kuhen.cryptopro.strategy.SmcSignal;
import com.kuhen.cryptopro.strategy.StrategyDecision;
import com.kuhen.cryptopro.strategy.StrategyEngine;
import com.kuhen.cryptopro.strategy.WeightedBiasResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.kuhen.cryptopro.trade.ClosedTrade;
import com.kuhen.cryptopro.trade.OpenTrade;
import com.kuhen.cryptopro.trade.TradeLifecycleManager;

@Service
@ConditionalOnProperty(prefix = "cryptopro.orchestrator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TradingOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(TradingOrchestratorService.class);

    private final MarketDataProvider marketDataProvider;
    private final PreprocessingService preprocessingService;
    private final StrategyEngine strategyEngine;
    private final SmcDetector smcDetector;
    private final DerivativesSignalFusionService derivativesSignalFusionService;
    private final SignalScoringModel signalScoringModel;
    private final AiProperties aiProperties;
    private final RiskManagementService riskManagementService;
    private final ExecutionEngineService executionEngineService;
    private final BinanceProperties binanceProperties;
    private final SignalTelemetryService signalTelemetryService;
    private final AccountSelectionService accountSelectionService;
    private final OpsTelemetryService opsTelemetryService;
    private final PaperPortfolioService paperPortfolioService;
    private final TradeLifecycleManager tradeLifecycleManager;

    private final AtomicBoolean cycleRunning = new AtomicBoolean(false);

    @Value("${cryptopro.orchestrator.retry-attempts:3}")
    private int retryAttempts;

    @Value("${cryptopro.orchestrator.retry-backoff-ms:300}")
    private long retryBackoffMs;

    /** Default constructor for framework instantiation. */
    public TradingOrchestratorService() {
        this.marketDataProvider = null;
        this.preprocessingService = null;
        this.strategyEngine = null;
        this.smcDetector = null;
        this.derivativesSignalFusionService = null;
        this.signalScoringModel = null;
        this.aiProperties = null;
        this.riskManagementService = null;
        this.executionEngineService = null;
        this.binanceProperties = null;
        this.signalTelemetryService = null;
        this.accountSelectionService = null;
        this.opsTelemetryService = null;
        this.paperPortfolioService = null;
        this.tradeLifecycleManager = null;
    }

    @Autowired
    public TradingOrchestratorService(
            MarketDataProvider marketDataProvider,
            PreprocessingService preprocessingService,
            StrategyEngine strategyEngine,
            SmcDetector smcDetector,
            DerivativesSignalFusionService derivativesSignalFusionService,
            SignalScoringModel signalScoringModel,
            AiProperties aiProperties,
            RiskManagementService riskManagementService,
            ExecutionEngineService executionEngineService,
            BinanceProperties binanceProperties,
            SignalTelemetryService signalTelemetryService,
            AccountSelectionService accountSelectionService,
            OpsTelemetryService opsTelemetryService,
            PaperPortfolioService paperPortfolioService,
            TradeLifecycleManager tradeLifecycleManager
    ) {
        this.marketDataProvider = marketDataProvider;
        this.preprocessingService = preprocessingService;
        this.strategyEngine = strategyEngine;
        this.smcDetector = smcDetector;
        this.derivativesSignalFusionService = derivativesSignalFusionService;
        this.signalScoringModel = signalScoringModel;
        this.aiProperties = aiProperties;
        this.riskManagementService = riskManagementService;
        this.executionEngineService = executionEngineService;
        this.binanceProperties = binanceProperties;
        this.signalTelemetryService = signalTelemetryService;
        this.accountSelectionService = accountSelectionService;
        this.opsTelemetryService = opsTelemetryService;
        this.paperPortfolioService = paperPortfolioService;
        this.tradeLifecycleManager = tradeLifecycleManager;
    }

    @Scheduled(fixedDelayString = "${cryptopro.orchestrator.fixed-delay-ms:30000}")
    public void runScheduled() {
        if (!cycleRunning.compareAndSet(false, true)) {
            log.warn("Skipping orchestrator cycle because previous cycle is still running");
            return;
        }

        try {
            CycleResult result = runOnce();
            log.info("Orchestrator cycle completed: symbols={}, success={}, failed={}",
                    result.processedSymbols(), result.successfulSymbols(), result.failedSymbols());
        } finally {
            cycleRunning.set(false);
        }
    }

    public CycleResult runOnce() {
        List<String> symbols = resolveSymbols();
        int success = 0;
        int failed = 0;

        for (String symbol : symbols) {
            try {
                runForSymbol(symbol);
                success++;
            } catch (Exception ex) {
                failed++;
                log.error("Orchestrator failed for symbol {}: {}", symbol, ex.getMessage(), ex);
            }
        }

        return new CycleResult(symbols.size(), success, failed);
    }

    private void runForSymbol(String symbol) {
        // 1) Fetch market data
        List<Candle> h1Candles = fetchWithRetry(() -> marketDataProvider.getRecentCandles(symbol, Timeframe.H1, 120), "h1-candles", symbol);
        List<Candle> m15Candles = fetchWithRetry(() -> marketDataProvider.getRecentCandles(symbol, Timeframe.M15, 120), "m15-candles", symbol);
        List<Candle> m5Candles = fetchWithRetry(() -> marketDataProvider.getRecentCandles(symbol, Timeframe.M5, 180), "m5-candles", symbol);
        OrderBookSnapshot orderBook = fetchWithRetry(() -> marketDataProvider.getLatestOrderBook(symbol), "order-book", symbol);
        FundingRate fundingRate = fetchWithRetry(() -> marketDataProvider.getLatestFundingRate(symbol), "funding-rate", symbol);
        List<OpenInterestSnapshot> openInterest = fetchWithRetry(() -> marketDataProvider.getRecentOpenInterest(symbol, 24), "open-interest", symbol);
        List<LiquidationEvent> liquidations = fetchWithRetry(() -> marketDataProvider.getRecentLiquidations(symbol, 50), "liquidations", symbol);

        if (h1Candles.isEmpty() || m15Candles.isEmpty() || m5Candles.isEmpty() || orderBook == null) {
            throw new IllegalStateException("Insufficient data to evaluate symbol " + symbol);
        }

        // 2) Generate features
        FeatureBundle features = generateFeatures(m5Candles);

        // 3) Run strategy
        PreprocessingResult h1 = preprocessingService.preprocess(h1Candles);
        PreprocessingResult m15 = preprocessingService.preprocess(m15Candles);
        PreprocessingResult m5 = preprocessingService.preprocess(m5Candles);

        WeightedBiasResult bias = strategyEngine.weightedBias(h1, m15, m5);
        SmcSignal smcSignal = smcDetector.detect(m5.candles());
        DerivativesSignal derivatives = derivativesSignalFusionService.evaluate(m5Candles, openInterest, fundingRate, liquidations);
        double quality = (h1.dataQualityScore() + m15.dataQualityScore() + m5.dataQualityScore()) / 3.0;
        StrategyDecision decision = strategyEngine.combine(bias, smcSignal, derivatives, quality);

        // 4) Score with AI
        SignalScoringFeatures aiFeatures = new SignalScoringFeatures(
                bias.score(),
                features.m5Rsi(),
                features.m5VolumeSpike(),
                derivatives.openInterestDelta() * 100.0,
                derivatives.fundingRate()
        );
        SignalScoringResult aiResult = signalScoringModel.score(aiFeatures);
        boolean aiPass = aiResult.probabilityOfSuccess() >= aiProperties.getThresholds().getMinProbability()
                && aiResult.confidence() >= aiProperties.getThresholds().getMinConfidence();

        // 5) Apply risk rules
        boolean tradableBeforeRisk = decision.tradable() && aiPass;
        double currentMidpoint = midpoint(orderBook);
        RiskManagementResult risk = riskManagementService.evaluate(new RiskManagementRequest(
                symbol,
                decision.direction(),
                tradableBeforeRisk,
                decision.sizeMultiplier(),
                currentMidpoint,
                m15Candles
        ));

        boolean tradable = tradableBeforeRisk && risk.allowed();
        AccountSelectionService.SelectedAccount selectedAccount = accountSelectionService == null
                ? null
                : accountSelectionService.resolveActiveExecutionAccount().orElse(null);
        double accountRiskMultiplier = selectedAccount != null && selectedAccount.riskMultiplier() > 0.0
                ? selectedAccount.riskMultiplier()
                : 1.0;
        double sizeMultiplier = tradable ? risk.adjustedSizeMultiplier() * accountRiskMultiplier : 0.0;
        reconcileOpenTrades(symbol, currentMidpoint, risk.atr(), decision.direction(), selectedAccount);

        // 6) Execute trades
        ExecutionResult execution = executionEngineService.execute(new ExecutionRequest(
                symbol,
                decision.direction(),
                tradable,
                sizeMultiplier,
                orderBook,
                selectedAccount != null ? selectedAccount.accountType() : null,
                selectedAccount != null ? selectedAccount.accountCode() : null
        ));
        riskManagementService.recordExecution(execution);

        OpenTrade openedTrade = null;
        if (execution.status() == ExecutionStatus.FILLED || execution.status() == ExecutionStatus.PARTIAL) {
            String provider = execution.provider() != null
                    ? execution.provider()
                    : selectedAccount != null ? selectedAccount.provider() : null;
            if (paperPortfolioService != null && "paper".equalsIgnoreCase(String.valueOf(provider))) {
                paperPortfolioService.applyExecution(symbol, decision.direction(), execution, currentMidpoint);
            }
            if (tradeLifecycleManager != null) {
                openedTrade = tradeLifecycleManager.openTrade(
                        symbol,
                        decision.direction(),
                        execution,
                        risk.stopLossPrice(),
                        risk.atr()
                );
            }
        }

        recordExecutionTelemetry(symbol, decision.direction(), execution, selectedAccount);

        recordOrchestratorSignal(symbol, decision, aiResult, smcSignal, derivatives, tradableBeforeRisk,
                risk, execution, features, orderBook, selectedAccount, openedTrade);

        log.info("{} | dir={} aiProb={} aiConf={} riskAllowed={} exec={} note={}",
                symbol,
                decision.direction(),
                round3(aiResult.probabilityOfSuccess()),
                round3(aiResult.confidence()),
                risk.allowed(),
                execution.status(),
                execution.notes());
    }

    private void recordOrchestratorSignal(String symbol,
                                          StrategyDecision decision,
                                          SignalScoringResult aiResult,
                                          SmcSignal smcSignal,
                                          DerivativesSignal derivatives,
                                          boolean tradableBeforeRisk,
                                          RiskManagementResult risk,
                                          ExecutionResult execution,
                                          FeatureBundle features,
                                          OrderBookSnapshot orderBook,
                                          AccountSelectionService.SelectedAccount selectedAccount,
                                          OpenTrade openedTrade) {
        if (signalTelemetryService == null || decision.direction() == com.kuhen.cryptopro.strategy.SignalDirection.NEUTRAL) {
            return;
        }

        boolean oiConfirmation = derivatives.direction() == decision.direction()
                && derivatives.direction() != com.kuhen.cryptopro.strategy.SignalDirection.NEUTRAL;
        boolean volumeSpike = features.m5VolumeSpike() > 1.5;
        Double entryPrice = execution.averageFillPrice() > 0.0 ? execution.averageFillPrice() : midpoint(orderBook);
        Double stopLoss = risk.stopLossPrice() > 0.0 ? risk.stopLossPrice() : null;
        Double takeProfit = openedTrade != null && openedTrade.getTakeProfitPrice() > 0.0
                ? openedTrade.getTakeProfitPrice()
                : deriveTakeProfit(decision.direction(), entryPrice, stopLoss);
        SignalOutcome outcome = switch (execution.status()) {
            case FILLED, PARTIAL -> SignalOutcome.EXECUTED;
            case REJECTED -> SignalOutcome.REJECTED;
            case NOT_SENT -> SignalOutcome.SKIPPED;
        };
        String accountCode = selectedAccount != null ? selectedAccount.accountCode() : execution.accountCode();
        String provider = execution.provider() != null ? execution.provider() : selectedAccount != null ? selectedAccount.provider() : null;

        String notes = "ORCH"
                + " aiProb=" + round3(aiResult.probabilityOfSuccess())
                + " aiConf=" + round3(aiResult.confidence())
                + " tradableBeforeRisk=" + tradableBeforeRisk
                + " riskAllowed=" + risk.allowed()
                + " exec=" + execution.status()
                + " provider=" + provider
                + " account=" + accountCode
                + " note=" + execution.notes();

        signalTelemetryService.record(new SignalLogEntry(
                java.time.Instant.now(),
                symbol,
                decision.direction(),
                decision.finalScore(),
                aiResult.probabilityOfSuccess(),
                smcSignal.liquiditySweep(),
                volumeSpike,
                oiConfirmation,
                decision.regime(),
                aiResult.confidence(),
                entryPrice,
                stopLoss,
                takeProfit,
                outcome,
                notes,
                execution.status().name(),
                accountCode,
                provider,
                openedTrade != null ? openedTrade.getTradeId() : null
        ));
    }

    private void recordExecutionTelemetry(
            String symbol,
            com.kuhen.cryptopro.strategy.SignalDirection direction,
            ExecutionResult execution,
            AccountSelectionService.SelectedAccount selectedAccount
    ) {
        if (opsTelemetryService == null) {
            return;
        }
        String provider = execution.provider() != null ? execution.provider() : selectedAccount != null ? selectedAccount.provider() : "paper";
        String accountCode = execution.accountCode() != null ? execution.accountCode() : selectedAccount != null ? selectedAccount.accountCode() : null;
        String source = "ORCHESTRATOR_" + String.valueOf(provider).toUpperCase(Locale.ROOT);
        opsTelemetryService.recordTransaction(new TransactionLogEntry(
                java.time.Instant.now(),
                source,
                symbol,
                direction.name(),
                execution.status().name(),
                execution.requestedQuantity(),
                execution.filledQuantity(),
                execution.slippageBps(),
                execution.notes(),
                accountCode,
                provider,
                execution.averageFillPrice() > 0.0 ? execution.averageFillPrice() : null
        ));
    }

    private void reconcileOpenTrades(String symbol,
                                     double currentPrice,
                                     double atr,
                                     com.kuhen.cryptopro.strategy.SignalDirection direction,
                                     AccountSelectionService.SelectedAccount selectedAccount) {
        if (tradeLifecycleManager == null || currentPrice <= 0.0) {
            return;
        }
        List<ClosedTrade> closedTrades = new ArrayList<>();
        closedTrades.addAll(tradeLifecycleManager.onPriceTick(symbol, currentPrice, atr));
        closedTrades.addAll(tradeLifecycleManager.evaluateForClose(symbol, currentPrice, direction));

        if (paperPortfolioService == null || closedTrades.isEmpty()) {
            return;
        }
        if (selectedAccount == null || !"paper".equalsIgnoreCase(String.valueOf(selectedAccount.provider()))) {
            return;
        }

        for (ClosedTrade closed : closedTrades) {
            com.kuhen.cryptopro.strategy.SignalDirection closeDirection =
                    closed.direction() == com.kuhen.cryptopro.strategy.SignalDirection.LONG
                            ? com.kuhen.cryptopro.strategy.SignalDirection.SHORT
                            : com.kuhen.cryptopro.strategy.SignalDirection.LONG;
            ExecutionResult syntheticClose = new ExecutionResult(
                    ExecutionStatus.FILLED,
                    closed.quantity(),
                    closed.quantity(),
                    closed.closePrice(),
                    closed.closePrice(),
                    0.0,
                    1,
                    List.of(),
                    "Synthetic close from trade lifecycle",
                    selectedAccount.provider(),
                    selectedAccount.accountType(),
                    selectedAccount.accountCode()
            );
            paperPortfolioService.applyExecution(closed.symbol(), closeDirection, syntheticClose, closed.closePrice());
        }
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

    private FeatureBundle generateFeatures(List<Candle> m5Candles) {
        List<Double> m5Closes = m5Candles.stream().map(Candle::close).toList();
        List<Double> m5Volumes = m5Candles.stream().map(Candle::volume).toList();

        Double m5Rsi = FeatureEngineeringUtil.calculateRsi(m5Closes, 14);
        Double m5VolMa = FeatureEngineeringUtil.calculateVolumeMA(m5Volumes, 20);
        double volumeSpike = (m5VolMa == null || m5VolMa <= 0.0) ? 1.0 : m5Volumes.get(m5Volumes.size() - 1) / m5VolMa;

        return new FeatureBundle(m5Rsi == null ? 50.0 : m5Rsi, volumeSpike);
    }

    private List<String> resolveSymbols() {
        if (binanceProperties.getSymbolMap() != null && !binanceProperties.getSymbolMap().isEmpty()) {
            return new ArrayList<>(binanceProperties.getSymbolMap().keySet());
        }
        return List.of(binanceProperties.getDefaultSymbol());
    }

    private double midpoint(OrderBookSnapshot orderBook) {
        if (orderBook.bids().isEmpty() || orderBook.asks().isEmpty()) {
            return 0.0;
        }
        return (orderBook.bids().get(0).price() + orderBook.asks().get(0).price()) / 2.0;
    }

    private <T> T fetchWithRetry(Supplier<T> action, String op, String symbol) {
        RuntimeException last = null;
        int attempts = Math.max(1, retryAttempts);
        for (int i = 1; i <= attempts; i++) {
            try {
                return action.get();
            } catch (RuntimeException ex) {
                last = ex;
                log.warn("Fetch retry {}/{} for {} ({}) failed: {}", i, attempts, symbol, op, ex.getMessage());
                if (i < attempts) {
                    try {
                        Thread.sleep(Math.max(50L, retryBackoffMs));
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw ex;
                    }
                }
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IllegalStateException("Unknown failure while fetching " + op);
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private record FeatureBundle(double m5Rsi, double m5VolumeSpike) {
    }

    public record CycleResult(int processedSymbols, int successfulSymbols, int failedSymbols) {
    }
}



