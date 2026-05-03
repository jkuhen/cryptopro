package com.kuhen.cryptopro.data.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "trades",
        indexes = {
                @Index(name = "idx_trades_instrument_time", columnList = "instrument_id, created_at DESC"),
                @Index(name = "idx_trades_status_time",     columnList = "status, created_at DESC"),
                @Index(name = "idx_trades_signal_id",       columnList = "signal_id")
        }
)
public class TradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "signal_id")
    private Long signalId;

    @Column(name = "entry_price", nullable = false)
    private double entryPrice;

    @Column(name = "exit_price")
    private Double exitPrice;

    @Column(name = "stop_loss")
    private Double stopLoss;

    @Column(name = "trailing_stop")
    private Double trailingStop;

    @Column(name = "take_profit")
    private Double takeProfit;

    @Column(name = "direction", length = 10)
    private String direction;

    @Column(name = "quantity")
    private Double quantity;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "pnl", nullable = false)
    private double pnl = 0.0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    public TradeEntity() {}

    // -----------------------------------------------------------------------
    // Getters / Setters
    // -----------------------------------------------------------------------

    public Long getId()                         { return id; }

    public Long getInstrumentId()               { return instrumentId; }
    public void setInstrumentId(Long v)         { this.instrumentId = v; }

    public Long getSignalId()                   { return signalId; }
    public void setSignalId(Long v)             { this.signalId = v; }

    public double getEntryPrice()               { return entryPrice; }
    public void setEntryPrice(double v)         { this.entryPrice = v; }

    public Double getExitPrice()                { return exitPrice; }
    public void setExitPrice(Double v)          { this.exitPrice = v; }

    public Double getStopLoss()                 { return stopLoss; }
    public void setStopLoss(Double v)           { this.stopLoss = v; }

    public Double getTrailingStop()             { return trailingStop; }
    public void setTrailingStop(Double v)       { this.trailingStop = v; }

    public Double getTakeProfit()               { return takeProfit; }
    public void setTakeProfit(Double v)         { this.takeProfit = v; }

    public String getDirection()                { return direction; }
    public void setDirection(String v)          { this.direction = v; }

    public Double getQuantity()                 { return quantity; }
    public void setQuantity(Double v)           { this.quantity = v; }

    public String getStatus()                   { return status; }
    public void setStatus(String v)             { this.status = v; }

    public double getPnl()                      { return pnl; }
    public void setPnl(double v)               { this.pnl = v; }

    public Instant getCreatedAt()               { return createdAt; }

    public Instant getClosedAt()                { return closedAt; }
    public void setClosedAt(Instant v)          { this.closedAt = v; }
}

