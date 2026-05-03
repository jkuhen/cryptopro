package com.kuhen.cryptopro.analytics;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * GET /api/v1/analytics/summary?symbol=BTCUSDT&from=2026-01-01T00:00:00Z&to=2026-04-26T00:00:00Z
     * Returns the full analytics summary for a symbol over a given window.
     * Omit {@code from}/{@code to} for all-time.
     */
    @GetMapping("/summary")
    public AnalyticsSummary summary(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return analyticsService.computeSummary(symbol, from, to);
    }

    /**
     * GET /api/v1/analytics/regime?symbol=BTCUSDT&from=2026-01-01T00:00:00Z
     * Returns win rate / profit factor broken down by detected market regime.
     */
    @GetMapping("/regime")
    public List<AnalyticsBreakdown> byRegime(
            @RequestParam String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from
    ) {
        return analyticsService.computeByRegime(symbol, from);
    }

    /**
     * GET /api/v1/analytics/conditions?symbol=BTCUSDT&from=2026-01-01T00:00:00Z
     * Returns performance split by entry conditions (liquidity sweep, volume spike, OI confirmation).
     */
    @GetMapping("/conditions")
    public List<AnalyticsBreakdown> byCondition(
            @RequestParam String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from
    ) {
        return analyticsService.computeByCondition(symbol, from);
    }

    /**
     * GET /api/v1/analytics/equity-curve?symbol=BTCUSDT&limit=200
     * Returns the cumulative equity curve for a symbol (chronological running sum of PnL).
     */
    @GetMapping("/equity-curve")
    public List<Double> equityCurve(
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "0") int limit
    ) {
        return analyticsService.getEquityCurve(symbol, limit);
    }

    /**
     * GET /api/v1/analytics/snapshots?symbol=BTCUSDT
     * Returns all stored performance snapshots for a symbol.
     */
    @GetMapping("/snapshots")
    public List<StrategyPerformanceSnapshotEntity> snapshots(
            @RequestParam String symbol
    ) {
        return analyticsService.getSnapshots(symbol);
    }

    /**
     * POST /api/v1/analytics/snapshots/refresh?symbol=BTCUSDT&window=ALL_TIME
     * Computes and persists a fresh performance snapshot for the requested window.
     */
    @PostMapping("/snapshots/refresh")
    public StrategyPerformanceSnapshotEntity refreshSnapshot(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "ALL_TIME") String window,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return analyticsService.computeAndPersistSnapshot(symbol, window, from, to);
    }
}

