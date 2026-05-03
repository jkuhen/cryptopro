package com.kuhen.cryptopro.execution;

import com.kuhen.cryptopro.config.ExecutionProperties;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.OrderBookLevel;
import com.kuhen.cryptopro.data.model.OrderBookSnapshot;
import com.kuhen.cryptopro.risk.RiskManagementRequest;
import com.kuhen.cryptopro.risk.RiskManagementResult;
import com.kuhen.cryptopro.risk.RiskManagementService;
import com.kuhen.cryptopro.strategy.SignalDirection;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ExecutionEngineService {

    private final ExecutionProperties executionProperties;
    private final LunoExecutionAdapter lunoExecutionAdapter;
    private final RiskManagementService riskManagementService;

    /** Default constructor for framework instantiation. */
    public ExecutionEngineService() {
        this.executionProperties = null;
        this.lunoExecutionAdapter = null;
        this.riskManagementService = null;
    }

    @Autowired
    public ExecutionEngineService(
            ExecutionProperties executionProperties,
            ObjectProvider<LunoExecutionAdapter> lunoExecutionAdapterProvider,
            ObjectProvider<RiskManagementService> riskManagementServiceProvider
    ) {
        this.executionProperties = executionProperties;
        this.lunoExecutionAdapter = lunoExecutionAdapterProvider.getIfAvailable();
        this.riskManagementService = riskManagementServiceProvider.getIfAvailable();
    }

    public ExecutionEngineService(ExecutionProperties executionProperties) {
        this.executionProperties = executionProperties;
        this.lunoExecutionAdapter = null;
        this.riskManagementService = null;
    }

    ExecutionEngineService(
            ExecutionProperties executionProperties,
            LunoExecutionAdapter lunoExecutionAdapter,
            RiskManagementService riskManagementService
    ) {
        this.executionProperties = executionProperties;
        this.lunoExecutionAdapter = lunoExecutionAdapter;
        this.riskManagementService = riskManagementService;
    }

    public ExecutionResult execute(ExecutionRequest request) {
        ExecutionRuntimeOverrides overrides = new ExecutionRuntimeOverrides(
                executionProperties.isEnabled(),
                executionProperties.getProvider(),
                executionProperties.getMarketType(),
                executionProperties.isLiveEnabled(),
                executionProperties.getBaseOrderQuantity(),
                executionProperties.getMaxPartialEntries(),
                executionProperties.getSlippageToleranceBps(),
                executionProperties.getLimitOffsetBps(),
                executionProperties.getMinSliceQuantity()
        );
        return execute(request, overrides);
    }

    public ExecutionResult execute(ExecutionRequest request, ExecutionRuntimeOverrides overrides) {
        ExecutionResult base;
        if (!overrides.enabled()) {
            base = ExecutionResult.notSent("Execution engine disabled (provider=" + overrides.provider() + ")");
        } else if (!request.tradable()) {
            base = ExecutionResult.notSent("Signal not tradable (provider=" + overrides.provider() + ")");
        } else if (request.direction() == SignalDirection.NEUTRAL) {
            base = ExecutionResult.notSent("Neutral direction has no executable order (provider=" + overrides.provider() + ")");
        } else if (isLunoProvider(overrides.provider())) {
            base = executeWithLuno(request, overrides);
        } else {
            base = executePaper(request, overrides);
        }
        return withContext(request, overrides, base);
    }

    public RiskManagedExecutionResult executeWithRisk(ExecutionRequest request, List<Candle> atrCandles, double currentPrice) {
        if (riskManagementService == null) {
            return new RiskManagedExecutionResult(
                    new RiskManagementResult(
                            request.tradable(),
                            List.of("Risk service unavailable; execution proceeded without risk adjustment"),
                            request.sizeMultiplier(),
                            0.0,
                            0.0,
                            0.0,
                            0.0,
                            0.0,
                            0,
                            0,
                            0.0
                    ),
                    execute(request)
            );
        }

        RiskManagementResult risk = riskManagementService.evaluate(new RiskManagementRequest(
                request.symbol(),
                request.direction(),
                request.tradable(),
                request.sizeMultiplier(),
                currentPrice,
                atrCandles
        ));

        ExecutionRequest adjustedRequest = new ExecutionRequest(
                request.symbol(),
                request.direction(),
                request.tradable() && risk.allowed(),
                request.tradable() && risk.allowed() ? risk.adjustedSizeMultiplier() : 0.0,
                request.orderBookSnapshot(),
                request.accountType(),
                request.accountCode()
        );

        ExecutionResult executionResult = execute(adjustedRequest);
        riskManagementService.recordExecution(executionResult);

        return new RiskManagedExecutionResult(risk, executionResult);
    }

    private ExecutionResult executeWithLuno(ExecutionRequest request, ExecutionRuntimeOverrides overrides) {
        if (!overrides.liveEnabled()) {
            return ExecutionResult.notSent("Luno execution configured but live execution is disabled");
        }
        if (lunoExecutionAdapter == null) {
            return ExecutionResult.notSent("Luno execution adapter unavailable");
        }

        OrderBookSnapshot book = request.orderBookSnapshot();
        if (book.bids().isEmpty() || book.asks().isEmpty()) {
            return new ExecutionResult(ExecutionStatus.REJECTED, 0.0, 0.0, 0.0, 0.0, 0.0, 0, List.of(), "Order book unavailable");
        }

        double requestedQuantity = Math.max(0.0, overrides.baseOrderQuantity() * request.sizeMultiplier());
        if (requestedQuantity < overrides.minSliceQuantity()) {
            return new ExecutionResult(ExecutionStatus.REJECTED, requestedQuantity, 0.0, 0.0, 0.0, 0.0, 0, List.of(), "Requested quantity below minimum slice quantity");
        }

        int slicesCount = Math.max(1, overrides.maxPartialEntries());
        double perSlice = requestedQuantity / slicesCount;

        double referencePrice = midpoint(book);
        double limitPrice = deriveLimitPrice(request.direction(), referencePrice, overrides.limitOffsetBps());

        List<ExecutionSlice> slices = new ArrayList<>(slicesCount);
        double totalFilledQty = 0.0;
        double totalFilledNotional = 0.0;
        int acceptedOrders = 0;

        for (int i = 0; i < slicesCount; i++) {
            try {
                String marketType = overrides.marketType();
                String orderId = lunoExecutionAdapter.submitMarketOrder(request.symbol(), request.direction(), perSlice, marketType);

                // Reconcile – poll the exchange until the order reaches a terminal state
                // so fill quantity and average price reflect actual exchange state.
                OrderStatusResult fillStatus = lunoExecutionAdapter.pollUntilTerminal(orderId, marketType);

                double sliceFilled = fillStatus.filledQuantity();
                double sliceAvgPrice = fillStatus.averagePrice() > 0.0
                        ? fillStatus.averagePrice()
                        : referencePrice; // fallback to mid if exchange returns 0

                ExecutionStatus sliceStatus;
                if (sliceFilled <= 0.0) {
                    sliceStatus = ExecutionStatus.REJECTED;
                } else if (sliceFilled < perSlice) {
                    sliceStatus = ExecutionStatus.PARTIAL;
                } else {
                    sliceStatus = ExecutionStatus.FILLED;
                }

                slices.add(new ExecutionSlice(
                        i + 1,
                        perSlice,
                        sliceFilled,
                        limitPrice,
                        sliceAvgPrice,
                        sliceStatus,
                        "Luno order " + orderId + " reconciled: state=" + fillStatus.rawState()
                ));
                if (sliceFilled > 0.0) {
                    totalFilledQty += sliceFilled;
                    totalFilledNotional += sliceFilled * sliceAvgPrice;
                    acceptedOrders++;
                }
            } catch (Exception ex) {
                slices.add(new ExecutionSlice(
                        i + 1,
                        perSlice,
                        0.0,
                        limitPrice,
                        0.0,
                        ExecutionStatus.REJECTED,
                        "Luno order failed: " + ex.getMessage()
                ));
            }
        }

        if (acceptedOrders == 0) {
            return new ExecutionResult(
                    ExecutionStatus.REJECTED,
                    requestedQuantity,
                    0.0,
                    limitPrice,
                    0.0,
                    0.0,
                    slicesCount,
                    slices,
                    "No Luno orders were accepted"
            );
        }

        double averageFillPrice = totalFilledQty == 0.0 ? 0.0 : totalFilledNotional / totalFilledQty;
        double slippageBps = calculateSlippageBps(request.direction(), referencePrice, averageFillPrice);

        if (totalFilledQty > 0.0 && slippageBps > overrides.slippageToleranceBps()) {
            return new ExecutionResult(
                    ExecutionStatus.REJECTED,
                    requestedQuantity,
                    totalFilledQty,
                    limitPrice,
                    averageFillPrice,
                    slippageBps,
                    slicesCount,
                    slices,
                    "Slippage above tolerance"
            );
        }

        ExecutionStatus finalStatus;
        if (totalFilledQty == 0.0) {
            finalStatus = ExecutionStatus.REJECTED;
        } else if (totalFilledQty < requestedQuantity) {
            finalStatus = ExecutionStatus.PARTIAL;
        } else {
            finalStatus = ExecutionStatus.FILLED;
        }

        String notes = finalStatus == ExecutionStatus.REJECTED
                ? "No slices filled within limit"
                : "Executed via Luno market orders (provider=" + overrides.provider()
                + ", marketType=" + overrides.marketType() + ", accepted=" + acceptedOrders + ")";

        return new ExecutionResult(
                finalStatus,
                requestedQuantity,
                totalFilledQty,
                limitPrice,
                averageFillPrice,
                slippageBps,
                slicesCount,
                slices,
                notes
        );
    }

    private ExecutionResult executePaper(ExecutionRequest request, ExecutionRuntimeOverrides overrides) {
        OrderBookSnapshot book = request.orderBookSnapshot();
        if (book.bids().isEmpty() || book.asks().isEmpty()) {
            return new ExecutionResult(ExecutionStatus.REJECTED, 0.0, 0.0, 0.0, 0.0, 0.0, 0, List.of(), "Order book unavailable");
        }

        double requestedQuantity = Math.max(0.0, overrides.baseOrderQuantity() * request.sizeMultiplier());
        if (requestedQuantity < overrides.minSliceQuantity()) {
            return new ExecutionResult(ExecutionStatus.REJECTED, requestedQuantity, 0.0, 0.0, 0.0, 0.0, 0, List.of(), "Requested quantity below minimum slice quantity");
        }

        int slicesCount = Math.max(1, overrides.maxPartialEntries());
        double perSlice = requestedQuantity / slicesCount;

        double referencePrice = midpoint(book);
        double limitPrice = deriveLimitPrice(request.direction(), referencePrice, overrides.limitOffsetBps());

        List<ExecutionSlice> slices = new ArrayList<>(slicesCount);
        double totalFilledQty = 0.0;
        double totalFilledNotional = 0.0;

        for (int i = 0; i < slicesCount; i++) {
            FillOutcome outcome = simulateFill(request.direction(), perSlice, limitPrice, book);
            ExecutionStatus status = outcome.filledQuantity <= 0.0
                    ? ExecutionStatus.REJECTED
                    : outcome.filledQuantity < perSlice ? ExecutionStatus.PARTIAL : ExecutionStatus.FILLED;

            slices.add(new ExecutionSlice(
                    i + 1,
                    perSlice,
                    outcome.filledQuantity,
                    limitPrice,
                    outcome.averagePrice,
                    status,
                    status == ExecutionStatus.REJECTED ? "No liquidity at or better than limit" : ""
            ));

            totalFilledQty += outcome.filledQuantity;
            totalFilledNotional += outcome.filledQuantity * outcome.averagePrice;
        }

        double averageFillPrice = totalFilledQty == 0.0 ? 0.0 : totalFilledNotional / totalFilledQty;
        double slippageBps = calculateSlippageBps(request.direction(), referencePrice, averageFillPrice);

        if (totalFilledQty > 0.0 && slippageBps > overrides.slippageToleranceBps()) {
            return new ExecutionResult(
                    ExecutionStatus.REJECTED,
                    requestedQuantity,
                    totalFilledQty,
                    limitPrice,
                    averageFillPrice,
                    slippageBps,
                    slicesCount,
                    slices,
                    "Slippage above tolerance"
            );
        }

        ExecutionStatus finalStatus;
        if (totalFilledQty == 0.0) {
            finalStatus = ExecutionStatus.REJECTED;
        } else if (totalFilledQty < requestedQuantity) {
            finalStatus = ExecutionStatus.PARTIAL;
        } else {
            finalStatus = ExecutionStatus.FILLED;
        }

        String notes = finalStatus == ExecutionStatus.REJECTED
                ? "No slices filled within limit"
                : "Executed using limit slices (provider=" + overrides.provider() + ")";

        return new ExecutionResult(
                finalStatus,
                requestedQuantity,
                totalFilledQty,
                limitPrice,
                averageFillPrice,
                slippageBps,
                slicesCount,
                slices,
                notes
        );
    }

    private boolean isLunoProvider(String provider) {
        return "luno".equals(String.valueOf(provider).toLowerCase(Locale.ROOT));
    }

    private ExecutionResult withContext(ExecutionRequest request, ExecutionRuntimeOverrides overrides, ExecutionResult base) {
        return new ExecutionResult(
                base.status(),
                base.requestedQuantity(),
                base.filledQuantity(),
                base.limitPrice(),
                base.averageFillPrice(),
                base.slippageBps(),
                base.partialEntries(),
                base.slices(),
                base.notes(),
                overrides.provider(),
                request.accountType(),
                request.accountCode()
        );
    }

    private FillOutcome simulateFill(SignalDirection direction, double quantity, double limitPrice, OrderBookSnapshot book) {
        List<OrderBookLevel> levels = direction == SignalDirection.LONG ? book.asks() : book.bids();

        double remaining = quantity;
        double filled = 0.0;
        double notional = 0.0;

        for (OrderBookLevel level : levels) {
            boolean priceAllowed = direction == SignalDirection.LONG ? level.price() <= limitPrice : level.price() >= limitPrice;
            if (!priceAllowed) {
                continue;
            }

            double qty = Math.min(remaining, level.size());
            if (qty <= 0.0) {
                continue;
            }

            filled += qty;
            notional += qty * level.price();
            remaining -= qty;

            if (remaining <= 0.0) {
                break;
            }
        }

        double average = filled == 0.0 ? 0.0 : notional / filled;
        return new FillOutcome(filled, average);
    }

    private double deriveLimitPrice(SignalDirection direction, double reference, double limitOffsetBps) {
        double offset = limitOffsetBps / 10_000.0;
        if (direction == SignalDirection.LONG) {
            return reference * (1.0 + offset);
        }
        return reference * (1.0 - offset);
    }

    private double midpoint(OrderBookSnapshot book) {
        double bestBid = book.bids().get(0).price();
        double bestAsk = book.asks().get(0).price();
        return (bestBid + bestAsk) / 2.0;
    }

    private double calculateSlippageBps(SignalDirection direction, double referencePrice, double averageFillPrice) {
        if (referencePrice <= 0.0 || averageFillPrice <= 0.0) {
            return 0.0;
        }

        double slippage = direction == SignalDirection.LONG
                ? (averageFillPrice - referencePrice) / referencePrice
                : (referencePrice - averageFillPrice) / referencePrice;
        return Math.max(0.0, slippage * 10_000.0);
    }

    private record FillOutcome(double filledQuantity, double averagePrice) {
    }

    public record ExecutionRuntimeOverrides(
            boolean enabled,
            String provider,
            String marketType,
            boolean liveEnabled,
            double baseOrderQuantity,
            int maxPartialEntries,
            double slippageToleranceBps,
            double limitOffsetBps,
            double minSliceQuantity
    ) {
    }
}


