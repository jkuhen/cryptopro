package com.kuhen.cryptopro.execution;

import com.kuhen.cryptopro.config.ExecutionProperties;
import com.kuhen.cryptopro.data.MarketDataProvider;
import com.kuhen.cryptopro.data.model.OrderBookLevel;
import com.kuhen.cryptopro.data.model.OrderBookSnapshot;
import com.kuhen.cryptopro.ops.OpsTelemetryService;
import com.kuhen.cryptopro.ops.PaperPortfolioService;
import com.kuhen.cryptopro.ops.TransactionLogEntry;
import com.kuhen.cryptopro.strategy.SignalDirection;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/execution")
public class ExecutionSimulationController {

    private final ExecutionEngineService executionEngineService;
    private final ExecutionProperties executionProperties;
    private final MarketDataProvider marketDataProvider;
    private final OpsTelemetryService opsTelemetryService;
    private final PaperPortfolioService paperPortfolioService;

    public ExecutionSimulationController(
            ExecutionEngineService executionEngineService,
            ExecutionProperties executionProperties,
            MarketDataProvider marketDataProvider,
            OpsTelemetryService opsTelemetryService,
            PaperPortfolioService paperPortfolioService
    ) {
        this.executionEngineService = executionEngineService;
        this.executionProperties = executionProperties;
        this.marketDataProvider = marketDataProvider;
        this.opsTelemetryService = opsTelemetryService;
        this.paperPortfolioService = paperPortfolioService;
    }

    @PostMapping("/simulate")
    public ExecutionResult simulate(@RequestBody ExecutionSimulationRequest request) {
        String symbol = request.symbol() == null || request.symbol().isBlank()
                ? "BTCUSDT"
                : request.symbol().toUpperCase();
        SignalDirection direction = request.direction() == null ? SignalDirection.NEUTRAL : request.direction();

        OrderBookSnapshot orderBook = buildOrderBook(symbol, request);

        ExecutionEngineService.ExecutionRuntimeOverrides overrides = new ExecutionEngineService.ExecutionRuntimeOverrides(
                true,
                executionProperties.getProvider(),
                executionProperties.getMarketType(),
                false,
                sanitizeQuantity(request.quantity()),
                sanitizeSlices(request.maxPartialEntries()),
                sanitizePositive(request.slippageToleranceBps(), executionProperties.getSlippageToleranceBps()),
                sanitizePositive(request.limitOffsetBps(), executionProperties.getLimitOffsetBps()),
                executionProperties.getMinSliceQuantity()
        );

        ExecutionRequest executionRequest = new ExecutionRequest(
                symbol,
                direction,
                request.tradable(),
                1.0,
                orderBook
        );

        ExecutionResult result = executionEngineService.execute(executionRequest, overrides);
        opsTelemetryService.recordTransaction(new TransactionLogEntry(
                Instant.now(),
                "EXECUTION_SIMULATION",
                symbol,
                direction.name(),
                result.status().name(),
                result.requestedQuantity(),
                result.filledQuantity(),
                result.slippageBps(),
                result.notes(),
                result.accountCode(),
                result.provider(),
                result.averageFillPrice() > 0.0 ? result.averageFillPrice() : null
        ));
        paperPortfolioService.applyExecution(symbol, direction, result, result.averageFillPrice());
        return result;
    }

    private OrderBookSnapshot buildOrderBook(String symbol, ExecutionSimulationRequest request) {
        if (hasCustomLevels(request.bids()) && hasCustomLevels(request.asks())) {
            List<OrderBookLevel> bids = request.bids().stream()
                    .map(level -> new OrderBookLevel(level.price(), level.size()))
                    .toList();
            List<OrderBookLevel> asks = request.asks().stream()
                    .map(level -> new OrderBookLevel(level.price(), level.size()))
                    .toList();
            return new OrderBookSnapshot(symbol, Instant.now(), bids, asks);
        }
        return marketDataProvider.getLatestOrderBook(symbol);
    }

    private boolean hasCustomLevels(List<ExecutionSimulationLevelRequest> levels) {
        return levels != null && !levels.isEmpty();
    }

    private double sanitizeQuantity(double quantity) {
        return quantity > 0.0 ? quantity : executionProperties.getBaseOrderQuantity();
    }

    private int sanitizeSlices(int maxPartialEntries) {
        return maxPartialEntries > 0 ? maxPartialEntries : executionProperties.getMaxPartialEntries();
    }

    private double sanitizePositive(double value, double fallback) {
        return value > 0.0 ? value : fallback;
    }
}


