package com.kuhen.cryptopro.analytics;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "trade_analytics",
        indexes = {
                @Index(name = "idx_trade_analytics_symbol_closed", columnList = "symbol, closed_at DESC"),
                @Index(name = "idx_trade_analytics_regime",        columnList = "regime, closed_at DESC"),
                @Index(name = "idx_trade_analytics_close_reason",  columnList = "close_reason, closed_at DESC")
        }
)
public class TradeAnalyticsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_uuid",        nullable = false, length = 36)  private String tradeUuid;
    @Column(name = "symbol",            nullable = false, length = 20)  private String symbol;
    @Column(name = "direction",         nullable = false, length = 10)  private String direction;
    @Column(name = "quantity",          nullable = false)               private double quantity;
    @Column(name = "entry_price",       nullable = false)               private double entryPrice;
    @Column(name = "close_price",       nullable = false)               private double closePrice;
    @Column(name = "pnl",               nullable = false)               private double pnl;
    @Column(name = "pnl_percent",       nullable = false)               private double pnlPercent;
    @Column(name = "close_reason",      nullable = false, length = 30)  private String closeReason;
    @Column(name = "regime",            length = 30)                    private String regime;
    @Column(name = "liquidity_sweep",   nullable = false)               private boolean liquiditySweep;
    @Column(name = "volume_spike",      nullable = false)               private boolean volumeSpike;
    @Column(name = "oi_confirmation",   nullable = false)               private boolean oiConfirmation;
    @Column(name = "hold_duration_sec", nullable = false)               private long holdDurationSec;
    @Column(name = "opened_at",         nullable = false)               private Instant openedAt;
    @Column(name = "closed_at",         nullable = false)               private Instant closedAt;
    @Column(name = "created_at",        nullable = false, updatable = false) private Instant createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public TradeAnalyticsEntity() {}

    // ---- Getters / setters ----
    public Long getId()                       { return id; }
    public String getTradeUuid()              { return tradeUuid; }
    public void setTradeUuid(String v)        { this.tradeUuid = v; }
    public String getSymbol()                 { return symbol; }
    public void setSymbol(String v)           { this.symbol = v; }
    public String getDirection()              { return direction; }
    public void setDirection(String v)        { this.direction = v; }
    public double getQuantity()               { return quantity; }
    public void setQuantity(double v)         { this.quantity = v; }
    public double getEntryPrice()             { return entryPrice; }
    public void setEntryPrice(double v)       { this.entryPrice = v; }
    public double getClosePrice()             { return closePrice; }
    public void setClosePrice(double v)       { this.closePrice = v; }
    public double getPnl()                    { return pnl; }
    public void setPnl(double v)              { this.pnl = v; }
    public double getPnlPercent()             { return pnlPercent; }
    public void setPnlPercent(double v)       { this.pnlPercent = v; }
    public String getCloseReason()            { return closeReason; }
    public void setCloseReason(String v)      { this.closeReason = v; }
    public String getRegime()                 { return regime; }
    public void setRegime(String v)           { this.regime = v; }
    public boolean isLiquiditySweep()         { return liquiditySweep; }
    public void setLiquiditySweep(boolean v)  { this.liquiditySweep = v; }
    public boolean isVolumeSpike()            { return volumeSpike; }
    public void setVolumeSpike(boolean v)     { this.volumeSpike = v; }
    public boolean isOiConfirmation()         { return oiConfirmation; }
    public void setOiConfirmation(boolean v)  { this.oiConfirmation = v; }
    public long getHoldDurationSec()          { return holdDurationSec; }
    public void setHoldDurationSec(long v)    { this.holdDurationSec = v; }
    public Instant getOpenedAt()              { return openedAt; }
    public void setOpenedAt(Instant v)        { this.openedAt = v; }
    public Instant getClosedAt()              { return closedAt; }
    public void setClosedAt(Instant v)        { this.closedAt = v; }
    public Instant getCreatedAt()             { return createdAt; }
}

