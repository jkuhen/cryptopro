package com.kuhen.cryptopro.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * JPA entity that persists calculated technical features for market analysis.
 *
 * <p>The natural key is {@code (instrument_id, recorded_at)}.  The
 * persistence layer uses an upsert (INSERT … ON CONFLICT DO UPDATE) so
 * late-arriving feature recalculations are handled gracefully.
 *
 * <p>Features include:
 * <ul>
 *   <li>EMA20, EMA50, EMA200 - Exponential Moving Averages</li>
 *   <li>RSI - Relative Strength Index</li>
 *   <li>ATR - Average True Range</li>
 *   <li>VOLUME_MA - Volume Moving Average</li>
 * </ul>
 */
@Entity
@Table(
        name = "features",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_features_instrument_timeframe_time",
                columnNames = {"instrument_id", "timeframe", "recorded_at"}
        )
)
public class FeaturesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "signal_id")
    private Long signalId;

    /** Timeframe this feature row was computed for, e.g. "M5", "M15", "H1". */
    @Column(name = "timeframe", nullable = false, length = 4)
    private String timeframe;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "ema20", nullable = false)
    private Double ema20;

    @Column(name = "ema50", nullable = false)
    private Double ema50;

    @Column(name = "ema200", nullable = false)
    private Double ema200;

    @Column(name = "rsi", nullable = false)
    private Double rsi;

    @Column(name = "atr", nullable = false)
    private Double atr;

    @Column(name = "volume_ma", nullable = false)
    private Double volumeMa;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // -------------------------------------------------------------------------
    // Getters / setters
    // -------------------------------------------------------------------------

    public Long getId()                    { return id; }
    public Long getInstrumentId()          { return instrumentId; }
    public void setInstrumentId(Long v)    { this.instrumentId = v; }
    public Long getSignalId()              { return signalId; }
    public void setSignalId(Long v)        { this.signalId = v; }
    public String getTimeframe()           { return timeframe; }
    public void setTimeframe(String v)     { this.timeframe = v; }
    public Instant getRecordedAt()         { return recordedAt; }
    public void setRecordedAt(Instant v)   { this.recordedAt = v; }
    public Double getEma20()               { return ema20; }
    public void setEma20(Double v)         { this.ema20 = v; }
    public Double getEma50()               { return ema50; }
    public void setEma50(Double v)         { this.ema50 = v; }
    public Double getEma200()              { return ema200; }
    public void setEma200(Double v)        { this.ema200 = v; }
    public Double getRsi()                 { return rsi; }
    public void setRsi(Double v)           { this.rsi = v; }
    public Double getAtr()                 { return atr; }
    public void setAtr(Double v)           { this.atr = v; }
    public Double getVolumeMa()            { return volumeMa; }
    public void setVolumeMa(Double v)      { this.volumeMa = v; }
    public Instant getCreatedAt()          { return createdAt; }
}

