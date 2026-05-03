package com.kuhen.cryptopro.analytics;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "strategy_performance_snapshot",
        indexes = {
                @Index(name = "idx_strategy_perf_symbol_window",
                       columnList = "symbol, window_label, computed_at DESC")
        }
)
public class StrategyPerformanceSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol",           nullable = false, length = 20) private String symbol;
    @Column(name = "window_label",     nullable = false, length = 20) private String windowLabel;
    @Column(name = "window_start")                                    private Instant windowStart;
    @Column(name = "window_end")                                      private Instant windowEnd;
    @Column(name = "total_trades",     nullable = false)              private int totalTrades;
    @Column(name = "wins",             nullable = false)              private int wins;
    @Column(name = "losses",           nullable = false)              private int losses;
    @Column(name = "win_rate_percent", nullable = false)              private double winRatePercent;
    @Column(name = "gross_profit",     nullable = false)              private double grossProfit;
    @Column(name = "gross_loss",       nullable = false)              private double grossLoss;
    @Column(name = "profit_factor",    nullable = false)              private double profitFactor;
    @Column(name = "total_pnl",        nullable = false)              private double totalPnl;
    @Column(name = "max_drawdown",     nullable = false)              private double maxDrawdown;
    @Column(name = "max_drawdown_pct", nullable = false)              private double maxDrawdownPct;
    @Column(name = "computed_at",      nullable = false)              private Instant computedAt;

    @PrePersist @PreUpdate
    protected void prePersist() {
        if (computedAt == null) computedAt = Instant.now();
    }

    public StrategyPerformanceSnapshotEntity() {}

    // ---- Getters / setters ----
    public Long getId()                          { return id; }
    public String getSymbol()                    { return symbol; }
    public void setSymbol(String v)              { this.symbol = v; }
    public String getWindowLabel()               { return windowLabel; }
    public void setWindowLabel(String v)         { this.windowLabel = v; }
    public Instant getWindowStart()              { return windowStart; }
    public void setWindowStart(Instant v)        { this.windowStart = v; }
    public Instant getWindowEnd()                { return windowEnd; }
    public void setWindowEnd(Instant v)          { this.windowEnd = v; }
    public int getTotalTrades()                  { return totalTrades; }
    public void setTotalTrades(int v)            { this.totalTrades = v; }
    public int getWins()                         { return wins; }
    public void setWins(int v)                   { this.wins = v; }
    public int getLosses()                       { return losses; }
    public void setLosses(int v)                 { this.losses = v; }
    public double getWinRatePercent()            { return winRatePercent; }
    public void setWinRatePercent(double v)      { this.winRatePercent = v; }
    public double getGrossProfit()               { return grossProfit; }
    public void setGrossProfit(double v)         { this.grossProfit = v; }
    public double getGrossLoss()                 { return grossLoss; }
    public void setGrossLoss(double v)           { this.grossLoss = v; }
    public double getProfitFactor()              { return profitFactor; }
    public void setProfitFactor(double v)        { this.profitFactor = v; }
    public double getTotalPnl()                  { return totalPnl; }
    public void setTotalPnl(double v)            { this.totalPnl = v; }
    public double getMaxDrawdown()               { return maxDrawdown; }
    public void setMaxDrawdown(double v)         { this.maxDrawdown = v; }
    public double getMaxDrawdownPct()            { return maxDrawdownPct; }
    public void setMaxDrawdownPct(double v)      { this.maxDrawdownPct = v; }
    public Instant getComputedAt()               { return computedAt; }
    public void setComputedAt(Instant v)         { this.computedAt = v; }
}

