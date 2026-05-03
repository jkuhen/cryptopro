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
 * JPA entity that persists a <em>closed</em> OHLCV candle received from
 * the Binance WebSocket kline stream.
 *
 * <p>The natural key is {@code (symbol, timeframe, open_time)}.  The
 * persistence layer uses an upsert (INSERT … ON CONFLICT DO UPDATE) so
 * late-arriving corrections are handled gracefully.
 */
@Entity
@Table(
        name = "ohlcv_candle",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ohlcv_candle",
                columnNames = {"symbol", "timeframe", "open_time"}
        )
)
public class OhlcvCandleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    /** String representation of {@link com.kuhen.cryptopro.data.model.Timeframe}, e.g. {@code "M1"}. */
    @Column(name = "timeframe", nullable = false, length = 10)
    private String timeframe;

    @Column(name = "open_time", nullable = false)
    private Instant openTime;

    @Column(name = "open_price", nullable = false)
    private double openPrice;

    @Column(name = "high_price", nullable = false)
    private double highPrice;

    @Column(name = "low_price", nullable = false)
    private double lowPrice;

    @Column(name = "close_price", nullable = false)
    private double closePrice;

    @Column(name = "volume", nullable = false)
    private double volume;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // -------------------------------------------------------------------------
    // Getters / setters (Hibernate requires them or field access)
    // -------------------------------------------------------------------------

    public Long getId()                  { return id; }
    public String getSymbol()            { return symbol; }
    public void setSymbol(String v)      { this.symbol = v; }
    public String getTimeframe()         { return timeframe; }
    public void setTimeframe(String v)   { this.timeframe = v; }
    public Instant getOpenTime()         { return openTime; }
    public void setOpenTime(Instant v)   { this.openTime = v; }
    public double getOpenPrice()         { return openPrice; }
    public void setOpenPrice(double v)   { this.openPrice = v; }
    public double getHighPrice()         { return highPrice; }
    public void setHighPrice(double v)   { this.highPrice = v; }
    public double getLowPrice()          { return lowPrice; }
    public void setLowPrice(double v)    { this.lowPrice = v; }
    public double getClosePrice()        { return closePrice; }
    public void setClosePrice(double v)  { this.closePrice = v; }
    public double getVolume()            { return volume; }
    public void setVolume(double v)      { this.volume = v; }
    public Instant getCreatedAt()        { return createdAt; }
}

