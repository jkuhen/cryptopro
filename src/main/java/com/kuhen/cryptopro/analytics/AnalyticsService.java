package com.kuhen.cryptopro.analytics;

import com.kuhen.cryptopro.trade.ClosedTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core analytics service.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Record per-trade analytics to the {@code trade_analytics} table after a trade closes.</li>
 *   <li>Compute {@link AnalyticsSummary} (win rate, profit factor, max drawdown) over any time window.</li>
 *   <li>Break down performance by regime, market conditions, direction, and close reason.</li>
 *   <li>Persist aggregated {@link StrategyPerformanceSnapshotEntity} snapshots on demand.</li>
 * </ul>
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final TradeAnalyticsRepository analyticsRepo;
    private final StrategyPerformanceSnapshotRepository snapshotRepo;

    public AnalyticsService(
            TradeAnalyticsRepository analyticsRepo,
            StrategyPerformanceSnapshotRepository snapshotRepo
    ) {
        this.analyticsRepo = analyticsRepo;
        this.snapshotRepo  = snapshotRepo;
    }

    // -----------------------------------------------------------------------
    // Recording
    // -----------------------------------------------------------------------

    /**
     * Records a closed trade's analytics to the database.
     * Call this from {@link com.kuhen.cryptopro.trade.TradeLifecycleManager} or
     * the execution pipeline whenever a trade reaches a terminal state.
     *
     * @param record rich context for the closed trade (regime, conditions)
     * @return the persisted entity
     */
    @Transactional
    public TradeAnalyticsEntity record(TradeAnalyticsRecord record) {
        ClosedTrade trade = record.closedTrade();

        TradeAnalyticsEntity entity = new TradeAnalyticsEntity();
        entity.setTradeUuid(trade.tradeId());
        entity.setSymbol(trade.symbol());
        entity.setDirection(trade.direction().name());
        entity.setQuantity(trade.quantity());
        entity.setEntryPrice(trade.entryPrice());
        entity.setClosePrice(trade.closePrice());
        entity.setPnl(trade.pnl());
        entity.setPnlPercent(trade.pnlPercent());
        entity.setCloseReason(trade.reason().name());
        entity.setRegime(record.regime() != null ? record.regime().name() : null);
        entity.setLiquiditySweep(record.liquiditySweep());
        entity.setVolumeSpike(record.volumeSpike());
        entity.setOiConfirmation(record.oiConfirmation());
        entity.setOpenedAt(trade.openedAt());
        entity.setClosedAt(trade.closedAt());
        entity.setHoldDurationSec(Duration.between(trade.openedAt(), trade.closedAt()).getSeconds());

        try {
            TradeAnalyticsEntity saved = analyticsRepo.save(entity);
            log.debug("Recorded analytics for trade {} ({} {}): pnl={}", trade.tradeId(), trade.direction(), trade.symbol(), trade.pnl());
            return saved;
        } catch (Exception ex) {
            log.warn("Failed to persist trade analytics for {}: {}", trade.tradeId(), ex.getMessage());
            return entity;
        }
    }

    // -----------------------------------------------------------------------
    // Summary computation
    // -----------------------------------------------------------------------

    /**
     * Computes a full {@link AnalyticsSummary} for a symbol over the requested window.
     *
     * @param symbol the trading pair (null = all symbols)
     * @param from   inclusive start; null = all time
     * @param to     inclusive end;   null = now
     */
    public AnalyticsSummary computeSummary(String symbol, Instant from, Instant to) {
        List<TradeAnalyticsEntity> rows = fetchRows(symbol, from, to);
        Instant effectiveFrom = from != null ? from : Instant.EPOCH;
        Instant effectiveTo   = to   != null ? to   : Instant.now();
        return buildSummary(symbol != null ? symbol : "ALL", effectiveFrom, effectiveTo, rows);
    }

    /**
     * Computes and persists a snapshot for a symbol + window label combination.
     *
     * @param symbol      the trading pair
     * @param windowLabel ALL_TIME | DAILY | WEEKLY
     * @param from        window start (null for ALL_TIME)
     * @param to          window end   (null = now)
     * @return the persisted snapshot entity
     */
    @Transactional
    public StrategyPerformanceSnapshotEntity computeAndPersistSnapshot(
            String symbol, String windowLabel, Instant from, Instant to
    ) {
        AnalyticsSummary summary = computeSummary(symbol, from, to);

        StrategyPerformanceSnapshotEntity snap = new StrategyPerformanceSnapshotEntity();
        snap.setSymbol(symbol != null ? symbol : "ALL");
        snap.setWindowLabel(windowLabel);
        snap.setWindowStart(from);
        snap.setWindowEnd(to != null ? to : Instant.now());
        snap.setTotalTrades(summary.totalTrades());
        snap.setWins(summary.wins());
        snap.setLosses(summary.losses());
        snap.setWinRatePercent(summary.winRatePercent());
        snap.setGrossProfit(summary.grossProfit());
        snap.setGrossLoss(summary.grossLoss());
        snap.setProfitFactor(summary.profitFactor());
        snap.setTotalPnl(summary.totalPnl());
        snap.setMaxDrawdown(summary.maxDrawdown());
        snap.setMaxDrawdownPct(summary.maxDrawdownPercent());
        snap.setComputedAt(Instant.now());

        return snapshotRepo.save(snap);
    }

    // -----------------------------------------------------------------------
    // Query helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the equity curve (cumulative PnL ordered by close time) for a symbol.
     *
     * @param symbol trading pair (null = all)
     * @param limit  max data points; 0 or negative = no limit
     * @return list of cumulative PnL values chronologically
     */
    public List<Double> getEquityCurve(String symbol, int limit) {
        List<TradeAnalyticsEntity> rows = symbol != null
                ? analyticsRepo.findBySymbolAndClosedAtAfterOrderByClosedAtAsc(symbol, Instant.EPOCH)
                : analyticsRepo.findAllByOrderByClosedAtAsc();

        List<Double> curve = new ArrayList<>();
        double cumulative = 0.0;
        for (TradeAnalyticsEntity row : rows) {
            cumulative += row.getPnl();
            curve.add(cumulative);
            if (limit > 0 && curve.size() >= limit) break;
        }
        return curve;
    }

    /**
     * Returns a per-regime performance breakdown for a symbol.
     */
    public List<AnalyticsBreakdown> computeByRegime(String symbol, Instant from) {
        Instant since = from != null ? from : Instant.EPOCH;
        List<TradeAnalyticsEntity> rows = analyticsRepo
                .findBySymbolAndClosedAtAfterOrderByClosedAtAsc(symbol, since);
        return breakdownByField(rows, TradeAnalyticsEntity::getRegime);
    }

    /**
     * Returns a per-condition performance breakdown for a symbol.
     * Conditions: liquiditySweep, volumeSpike, oiConfirmation.
     */
    public List<AnalyticsBreakdown> computeByCondition(String symbol, Instant from) {
        Instant since = from != null ? from : Instant.EPOCH;
        List<TradeAnalyticsEntity> rows = analyticsRepo
                .findBySymbolAndClosedAtAfterOrderByClosedAtAsc(symbol, since);

        List<AnalyticsBreakdown> result = new ArrayList<>();
        result.add(buildBreakdown("liquiditySweep_true",  rows.stream().filter(TradeAnalyticsEntity::isLiquiditySweep).toList()));
        result.add(buildBreakdown("liquiditySweep_false", rows.stream().filter(r -> !r.isLiquiditySweep()).toList()));
        result.add(buildBreakdown("volumeSpike_true",     rows.stream().filter(TradeAnalyticsEntity::isVolumeSpike).toList()));
        result.add(buildBreakdown("volumeSpike_false",    rows.stream().filter(r -> !r.isVolumeSpike()).toList()));
        result.add(buildBreakdown("oiConfirmation_true",  rows.stream().filter(TradeAnalyticsEntity::isOiConfirmation).toList()));
        result.add(buildBreakdown("oiConfirmation_false", rows.stream().filter(r -> !r.isOiConfirmation()).toList()));
        return result;
    }

    /**
     * Returns all stored snapshots for a symbol in newest-first order.
     */
    public List<StrategyPerformanceSnapshotEntity> getSnapshots(String symbol) {
        return snapshotRepo.findAllBySymbol(symbol);
    }

    // -----------------------------------------------------------------------
    // Private computation
    // -----------------------------------------------------------------------

    private AnalyticsSummary buildSummary(
            String symbol, Instant from, Instant to, List<TradeAnalyticsEntity> rows
    ) {
        if (rows.isEmpty()) {
            return emptySum(symbol, from, to);
        }

        int wins = 0, losses = 0;
        double grossProfit = 0, grossLoss = 0, totalPnl = 0;
        long totalHold = 0;

        for (TradeAnalyticsEntity r : rows) {
            totalPnl += r.getPnl();
            totalHold += r.getHoldDurationSec();
            if (r.getPnl() > 0) { wins++;   grossProfit += r.getPnl(); }
            else if (r.getPnl() < 0) { losses++; grossLoss += r.getPnl(); }
        }

        int total = rows.size();
        double winRate = total == 0 ? 0 : round2((wins * 100.0) / total);
        double profitFactor = grossLoss == 0 ? (grossProfit > 0 ? Double.MAX_VALUE : 0) :
                round2(grossProfit / Math.abs(grossLoss));
        double avgWin  = wins   == 0 ? 0 : round2(grossProfit / wins);
        double avgLoss = losses == 0 ? 0 : round2(grossLoss   / losses);

        double[] ddResult = computeMaxDrawdown(rows);

        List<AnalyticsBreakdown> byRegime    = breakdownByField(rows, TradeAnalyticsEntity::getRegime);
        List<AnalyticsBreakdown> byDirection = breakdownByField(rows, TradeAnalyticsEntity::getDirection);
        List<AnalyticsBreakdown> byReason    = breakdownByField(rows, TradeAnalyticsEntity::getCloseReason);

        List<AnalyticsBreakdown> byCondition = new ArrayList<>();
        byCondition.add(buildBreakdown("liquiditySweep_true",  rows.stream().filter(TradeAnalyticsEntity::isLiquiditySweep).toList()));
        byCondition.add(buildBreakdown("volumeSpike_true",     rows.stream().filter(TradeAnalyticsEntity::isVolumeSpike).toList()));
        byCondition.add(buildBreakdown("oiConfirmation_true",  rows.stream().filter(TradeAnalyticsEntity::isOiConfirmation).toList()));

        return new AnalyticsSummary(
                symbol, from, to,
                total, wins, losses,
                winRate, round2(grossProfit), round2(grossLoss),
                profitFactor, round2(totalPnl),
                round2(ddResult[0]), round2(ddResult[1]),
                avgWin, avgLoss,
                total == 0 ? 0 : totalHold / total,
                byRegime, byCondition, byReason, byDirection
        );
    }

    /**
     * Computes maximum drawdown in absolute and percentage terms.
     * Drawdown = peak equity – trough equity during  any consecutive sequence.
     *
     * @return [maxDrawdown, maxDrawdownPercent]
     */
    private double[] computeMaxDrawdown(List<TradeAnalyticsEntity> rows) {
        double peak        = 0.0;
        double equity      = 0.0;
        double maxDD       = 0.0;
        double maxDDPct    = 0.0;

        for (TradeAnalyticsEntity row : rows) {
            equity += row.getPnl();
            if (equity > peak) {
                peak = equity;
            }
            double dd = peak - equity;
            if (dd > maxDD) {
                maxDD    = dd;
                maxDDPct = peak > 0 ? (dd / peak) * 100.0 : 0.0;
            }
        }
        return new double[]{maxDD, maxDDPct};
    }

    /** Groups trades by a string field and computes a {@link AnalyticsBreakdown} per unique value. */
    private List<AnalyticsBreakdown> breakdownByField(
            List<TradeAnalyticsEntity> rows,
            java.util.function.Function<TradeAnalyticsEntity, String> keyFn
    ) {
        Map<String, List<TradeAnalyticsEntity>> groups = rows.stream()
                .collect(Collectors.groupingBy(r -> {
                    String k = keyFn.apply(r);
                    return k != null ? k : "UNKNOWN";
                }, LinkedHashMap::new, Collectors.toList()));

        return groups.entrySet().stream()
                .map(e -> buildBreakdown(e.getKey(), e.getValue()))
                .toList();
    }

    private AnalyticsBreakdown buildBreakdown(String label, List<TradeAnalyticsEntity> rows) {
        int wins = 0, losses = 0;
        double gp = 0, gl = 0, tp = 0;
        for (TradeAnalyticsEntity r : rows) {
            tp += r.getPnl();
            if (r.getPnl() > 0) { wins++; gp += r.getPnl(); }
            else if (r.getPnl() < 0) { losses++; gl += r.getPnl(); }
        }
        int total = rows.size();
        double wr = total == 0 ? 0 : round2((wins * 100.0) / total);
        double pf = gl == 0 ? (gp > 0 ? Double.MAX_VALUE : 0) : round2(gp / Math.abs(gl));
        return new AnalyticsBreakdown(label, total, wins, losses, wr, round2(tp), pf);
    }

    private List<TradeAnalyticsEntity> fetchRows(String symbol, Instant from, Instant to) {
        if (symbol == null) {
            return analyticsRepo.findAllByOrderByClosedAtAsc();
        }
        if (from == null && to == null) {
            return analyticsRepo.findBySymbolAndClosedAtAfterOrderByClosedAtAsc(symbol, Instant.EPOCH);
        }
        Instant effectiveFrom = from != null ? from : Instant.EPOCH;
        Instant effectiveTo   = to   != null ? to   : Instant.now();
        return analyticsRepo.findBySymbolAndClosedAtBetweenOrderByClosedAtAsc(symbol, effectiveFrom, effectiveTo);
    }

    private AnalyticsSummary emptySum(String symbol, Instant from, Instant to) {
        return new AnalyticsSummary(symbol, from, to,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), List.of(), List.of(), List.of());
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

