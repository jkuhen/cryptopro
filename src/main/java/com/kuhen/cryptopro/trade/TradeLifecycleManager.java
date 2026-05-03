package com.kuhen.cryptopro.trade;

import com.kuhen.cryptopro.analytics.AnalyticsService;
import com.kuhen.cryptopro.analytics.TradeAnalyticsRecord;
import com.kuhen.cryptopro.config.TradeLifecycleProperties;
import com.kuhen.cryptopro.data.entity.TradeEntity;
import com.kuhen.cryptopro.data.repository.TradeRepository;
import com.kuhen.cryptopro.execution.ExecutionResult;
import com.kuhen.cryptopro.execution.ExecutionStatus;
import com.kuhen.cryptopro.ops.SignalOutcome;
import com.kuhen.cryptopro.ops.SignalTelemetryService;
import com.kuhen.cryptopro.risk.RiskStateService;
import com.kuhen.cryptopro.strategy.SignalDirection;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the full lifecycle of open trades:
 * <ul>
 *   <li>Opens a trade after a successful execution.</li>
 *   <li>Updates trailing stops on every price tick.</li>
 *   <li>Automatically closes trades on stop-loss hit, take-profit hit,
 *       or signal reversal.</li>
 *   <li>Persists lifecycle events to the {@code trades} table.</li>
 *   <li>Keeps {@link RiskStateService} in sync (concurrent trade count,
 *       realised daily P&L).</li>
 * </ul>
 */
@Service
public class TradeLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(TradeLifecycleManager.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final TradeLifecycleProperties props;
    private final TradeRepository tradeRepository;
    private final RiskStateService riskStateService;
    private final AnalyticsService analyticsService;
    private final SignalTelemetryService signalTelemetryService;

    /** Live trade registry keyed by internal {@link OpenTrade#getTradeId()}. */
    private final Map<String, OpenTrade> openTrades = new ConcurrentHashMap<>();

    /** Default constructor for framework instantiation. */
    public TradeLifecycleManager() {
        this.props = null;
        this.tradeRepository = null;
        this.riskStateService = null;
        this.analyticsService = null;
        this.signalTelemetryService = null;
    }

    @Autowired
    public TradeLifecycleManager(
            TradeLifecycleProperties props,
            TradeRepository tradeRepository,
            RiskStateService riskStateService,
            ObjectProvider<AnalyticsService> analyticsServiceProvider,
            ObjectProvider<SignalTelemetryService> signalTelemetryServiceProvider
    ) {
        this.props = props;
        this.tradeRepository = tradeRepository;
        this.riskStateService = riskStateService;
        this.analyticsService = analyticsServiceProvider.getIfAvailable();
        this.signalTelemetryService = signalTelemetryServiceProvider.getIfAvailable();
    }

    /** Test-friendly constructor without analytics. */
    TradeLifecycleManager(
            TradeLifecycleProperties props,
            TradeRepository tradeRepository,
            RiskStateService riskStateService
    ) {
        this.props = props;
        this.tradeRepository = tradeRepository;
        this.riskStateService = riskStateService;
        this.analyticsService = null;
        this.signalTelemetryService = null;
    }

    @PostConstruct
    void restoreOpenTradesFromPersistence() {
        if (tradeRepository == null || props == null || !props.isEnabled()) {
            return;
        }
        try {
            List<TradeEntity> persistedOpen = tradeRepository.findAllOpen();
            for (TradeEntity entity : persistedOpen) {
                String symbol = resolveSymbolByInstrumentId(entity.getInstrumentId());
                if (symbol == null || symbol.isBlank()) {
                    continue;
                }
                SignalDirection direction = parseDirection(entity.getDirection());
                if (direction == SignalDirection.NEUTRAL) {
                    continue;
                }
                double qty = entity.getQuantity() == null ? 0.0 : entity.getQuantity();
                if (qty <= 0.0 || entity.getEntryPrice() <= 0.0) {
                    continue;
                }
                double trailing = entity.getTrailingStop() != null && entity.getTrailingStop() > 0.0
                        ? entity.getTrailingStop()
                        : (entity.getStopLoss() != null && entity.getStopLoss() > 0.0 ? entity.getStopLoss() : entity.getEntryPrice());
                double takeProfit = entity.getTakeProfit() == null ? 0.0 : entity.getTakeProfit();

                OpenTrade trade = new OpenTrade(
                        symbol,
                        direction,
                        qty,
                        entity.getEntryPrice(),
                        trailing,
                        takeProfit,
                        0.0
                );
                trade.setDbTradeId(entity.getId());
                openTrades.put(trade.getTradeId(), trade);
                if (riskStateService != null) {
                    riskStateService.recordExecution(new ExecutionResult(
                            ExecutionStatus.FILLED,
                            qty,
                            qty,
                            entity.getEntryPrice(),
                            entity.getEntryPrice(),
                            0.0,
                            1,
                            List.of(),
                            "Restored open trade from DB"
                    ));
                }
            }
            if (!persistedOpen.isEmpty()) {
                log.info("Restored {} open trade(s) from DB", openTrades.size());
            }
        } catch (Exception ex) {
            log.warn("Failed to restore open trades from DB: {}", ex.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Open
    // -----------------------------------------------------------------------

    /**
     * Registers a new open trade derived from a successful execution result.
     *
     * @param symbol    trading pair (e.g. "BTCUSDT")
     * @param direction LONG or SHORT
     * @param result    the {@link ExecutionResult} from the execution engine
     * @param stopLoss  ATR-based initial stop loss price
     * @param atr       ATR at the moment of entry
     * @param signalId  optional database ID of the originating signal ({@code null} if not available)
     * @return the registered {@link OpenTrade}, or {@code null} if rejected/not tradable
     */
    public OpenTrade openTrade(
            String symbol,
            SignalDirection direction,
            ExecutionResult result,
            double stopLoss,
            double atr,
            Long signalId
    ) {
        if (!props.isEnabled()) {
            return null;
        }
        if (result.status() != ExecutionStatus.FILLED && result.status() != ExecutionStatus.PARTIAL) {
            return null;
        }
        if (result.filledQuantity() <= 0.0 || result.averageFillPrice() <= 0.0) {
            return null;
        }

        double entryPrice   = result.averageFillPrice();
        double takeProfitPrice = deriveTakeProfit(direction, entryPrice, atr);

        OpenTrade trade = new OpenTrade(
                symbol,
                direction,
                result.filledQuantity(),
                entryPrice,
                stopLoss,
                takeProfitPrice,
                atr
        );

        openTrades.put(trade.getTradeId(), trade);
        persistOpen(trade, signalId);

        log.info("Opened trade {} | {} {} @ {} | SL={} TP={}",
                trade.getTradeId(), direction, symbol, entryPrice, stopLoss, takeProfitPrice);

        return trade;
    }

    /**
     * Registers a new open trade derived from a successful execution result.
     * Overload for backward compatibility when signal ID is not available.
     *
     * @param symbol    trading pair (e.g. "BTCUSDT")
     * @param direction LONG or SHORT
     * @param result    the {@link ExecutionResult} from the execution engine
     * @param stopLoss  ATR-based initial stop loss price
     * @param atr       ATR at the moment of entry
     * @return the registered {@link OpenTrade}, or {@code null} if rejected/not tradable
     */
    public OpenTrade openTrade(
            String symbol,
            SignalDirection direction,
            ExecutionResult result,
            double stopLoss,
            double atr
    ) {
        return openTrade(symbol, direction, result, stopLoss, atr, null);
    }

    // -----------------------------------------------------------------------
    // Monitor / Tick
    // -----------------------------------------------------------------------

    /**
     * Processes a price tick for a given symbol.
     * <p>
     * For each open trade on that symbol:
     * <ol>
     *   <li>Ratchets the trailing stop upward (LONG) or downward (SHORT).</li>
     *   <li>Checks whether the stop-loss or take-profit level has been hit.</li>
     *   <li>Automatically closes any triggered trades and returns them.</li>
     * </ol>
     *
     * @param symbol       the trading pair
     * @param currentPrice latest market price
     * @param atr          latest ATR for the trailing stop calculation
     * @return list of trades that were automatically closed during this tick
     */
    public List<ClosedTrade> onPriceTick(String symbol, double currentPrice, double atr) {
        List<ClosedTrade> closed = new ArrayList<>();
        for (OpenTrade trade : openTradesFor(symbol)) {
            trade.updateTrailingStop(currentPrice, atr, props.getTrailingStopAtrMultiplier());

            if (trade.isTakeProfitHit(currentPrice)) {
                closed.add(closeTrade(trade.getTradeId(), currentPrice, TradeCloseReason.TAKE_PROFIT_HIT));
            } else if (trade.isStopLossHit(currentPrice)) {
                closed.add(closeTrade(trade.getTradeId(), currentPrice, TradeCloseReason.STOP_LOSS_HIT));
            }
        }
        return closed;
    }

    // -----------------------------------------------------------------------
    // Signal-driven evaluation
    // -----------------------------------------------------------------------

    /**
     * Evaluates open trades for a symbol against a new strategy signal.
     * If {@code close-on-signal-reversal} is enabled, any trade whose
     * direction opposes the new signal is immediately closed.
     *
     * @param symbol       the trading pair
     * @param currentPrice current market price used as the close price
     * @param newDirection the latest signal direction from the strategy engine
     * @return list of trades closed due to signal reversal
     */
    public List<ClosedTrade> evaluateForClose(
            String symbol,
            double currentPrice,
            SignalDirection newDirection
    ) {
        List<ClosedTrade> closed = new ArrayList<>();
        if (!props.isCloseOnSignalReversal() || newDirection == SignalDirection.NEUTRAL) {
            return closed;
        }
        for (OpenTrade trade : openTradesFor(symbol)) {
            if (trade.isSignalReversal(newDirection)) {
                closed.add(closeTrade(trade.getTradeId(), currentPrice, TradeCloseReason.SIGNAL_REVERSAL));
            }
        }
        return closed;
    }

    // -----------------------------------------------------------------------
    // Close
    // -----------------------------------------------------------------------

    /**
     * Explicitly closes the trade with the given internal ID at the given price.
     * Idempotent: if the trade is already gone, returns {@code null}.
     *
     * @param tradeId    internal {@link OpenTrade#getTradeId()}
     * @param closePrice price at which the trade is closed
     * @param reason     reason for closure
     * @return the {@link ClosedTrade} snapshot, or {@code null} if not found
     */
    public ClosedTrade closeTrade(String tradeId, double closePrice, TradeCloseReason reason) {
        OpenTrade trade = openTrades.remove(tradeId);
        if (trade == null) {
            log.warn("closeTrade called for unknown/already-closed trade {}", tradeId);
            return null;
        }

        ClosedTrade closed = ClosedTrade.of(trade, closePrice, reason);
        persistClosed(trade, closed);
        riskStateService.recordTradeClosed(closed);
        if (signalTelemetryService != null) {
            signalTelemetryService.markTradeClosed(
                    trade.getTradeId(),
                    deriveSignalOutcome(closed),
                    "tradeClosed reason=" + reason + " pnl=" + Math.round(closed.pnl() * 100.0) / 100.0
            );
        }
        if (analyticsService != null) {
            analyticsService.record(TradeAnalyticsRecord.of(closed));
        }

        log.info("Closed trade {} | {} {} | reason={} | pnl={} ({}%)",
                tradeId, trade.getDirection(), trade.getSymbol(),
                reason, closed.pnl(), closed.pnlPercent());

        return closed;
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    /** Returns an unmodifiable snapshot of all currently open trades. */
    public Collection<OpenTrade> getOpenTrades() {
        return List.copyOf(openTrades.values());
    }

    /** Returns open trades for a specific symbol. */
    public List<OpenTrade> getOpenTrades(String symbol) {
        return openTradesFor(symbol);
    }

    /** Finds an open trade by its internal ID. */
    public Optional<OpenTrade> findById(String tradeId) {
        return Optional.ofNullable(openTrades.get(tradeId));
    }

    /** Number of currently open trades. */
    public int openTradeCount() {
        return openTrades.size();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private List<OpenTrade> openTradesFor(String symbol) {
        return openTrades.values().stream()
                .filter(t -> t.getSymbol().equals(symbol))
                .toList();
    }

    private double deriveTakeProfit(SignalDirection direction, double entryPrice, double atr) {
        double multiplier = props.getTakeProfitAtrMultiplier();
        if (multiplier <= 0.0 || atr <= 0.0) {
            return 0.0;
        }
        double distance = atr * multiplier;
        return direction == SignalDirection.LONG
                ? entryPrice + distance
                : entryPrice - distance;
    }

    private void persistOpen(OpenTrade trade, Long signalId) {
        try {
            TradeEntity entity = new TradeEntity();
            entity.setEntryPrice(trade.getEntryPrice());
            entity.setStopLoss(trade.getInitialStopLoss());
            entity.setTrailingStop(trade.getTrailingStopLoss());
            entity.setTakeProfit(trade.getTakeProfitPrice() > 0.0 ? trade.getTakeProfitPrice() : null);
            entity.setDirection(trade.getDirection().name());
            entity.setQuantity(trade.getQuantity());
            entity.setStatus("OPEN");
            entity.setPnl(0.0);
            // Link to the originating signal if available
            if (signalId != null) {
                entity.setSignalId(signalId);
            }
            Long instrumentId = resolveInstrumentId(trade.getSymbol());
            entity.setInstrumentId(instrumentId == null ? 0L : instrumentId);
            TradeEntity saved = tradeRepository.save(entity);
            trade.setDbTradeId(saved.getId());
        } catch (Exception ex) {
            log.warn("Failed to persist opened trade {}: {}", trade.getTradeId(), ex.getMessage());
        }
    }

    private void persistClosed(OpenTrade trade, ClosedTrade closed) {
        if (trade.getDbTradeId() == null) {
            return;
        }
        tradeRepository.findById(trade.getDbTradeId()).ifPresent(entity -> {
            entity.setExitPrice(closed.closePrice());
            entity.setTrailingStop(trade.getTrailingStopLoss());
            entity.setStatus(mapReasonToStatus(closed.reason()));
            entity.setPnl(closed.pnl());
            entity.setClosedAt(closed.closedAt());
            tradeRepository.save(entity);
        });
    }

    private String mapReasonToStatus(TradeCloseReason reason) {
        return switch (reason) {
            case STOP_LOSS_HIT   -> "STOP_LOSS_HIT";
            case TAKE_PROFIT_HIT -> "TAKE_PROFIT_HIT";
            case SIGNAL_REVERSAL, MANUAL -> "CLOSED";
        };
    }

    private Long resolveInstrumentId(String symbol) {
        if (entityManager == null || symbol == null || symbol.isBlank()) {
            return null;
        }
        try {
            Object result = entityManager.createNativeQuery("""
                    SELECT id FROM instrument
                    WHERE exchange_name = 'BINANCE' AND symbol = ?1
                    LIMIT 1
                    """)
                    .setParameter(1, symbol)
                    .getSingleResult();
            return ((Number) result).longValue();
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveSymbolByInstrumentId(Long instrumentId) {
        if (entityManager == null || instrumentId == null) {
            return null;
        }
        try {
            Object result = entityManager.createNativeQuery("SELECT symbol FROM instrument WHERE id = ?1")
                    .setParameter(1, instrumentId)
                    .getSingleResult();
            return result == null ? null : result.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private SignalDirection parseDirection(String direction) {
        try {
            return SignalDirection.valueOf(String.valueOf(direction).toUpperCase());
        } catch (Exception ex) {
            return SignalDirection.NEUTRAL;
        }
    }

    private SignalOutcome deriveSignalOutcome(ClosedTrade closed) {
        if (closed.pnl() > 0.0) {
            return SignalOutcome.WIN;
        }
        if (closed.pnl() < 0.0) {
            return SignalOutcome.LOSS;
        }
        return SignalOutcome.SKIPPED;
    }
}




