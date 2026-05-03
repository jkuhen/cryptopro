package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.config.KrakenProperties;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.FeedStatus;
import com.kuhen.cryptopro.data.model.FundingRate;
import com.kuhen.cryptopro.data.model.LiquidationEvent;
import com.kuhen.cryptopro.data.model.LiquidationSide;
import com.kuhen.cryptopro.data.model.OpenInterestSnapshot;
import com.kuhen.cryptopro.data.model.OrderBookLevel;
import com.kuhen.cryptopro.data.model.OrderBookSnapshot;
import com.kuhen.cryptopro.data.model.Timeframe;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@ConditionalOnProperty(prefix = "cryptopro.data", name = "provider", havingValue = "kraken")
public class KrakenMarketDataProvider implements MarketDataProvider {

    private final KrakenProperties krakenProperties;
    private final CandleCache candleCache;
    private final KrakenRealtimeCache realtimeCache;

    private volatile long lastLatencyMs = 0L;
    private volatile long lastFeedTimestampMs = 0L;

    public KrakenMarketDataProvider(KrakenProperties krakenProperties,
                                    CandleCache candleCache,
                                    KrakenRealtimeCache realtimeCache) {
        this.krakenProperties = krakenProperties;
        this.candleCache = candleCache;
        this.realtimeCache = realtimeCache;
    }

    @Override
    public List<Candle> getRecentCandles(String symbol, Timeframe timeframe, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        String normalized = normalizeSymbol(symbol);

        List<Candle> cached = candleCache.getCandles(normalized, timeframe, safeLimit);
        if (!cached.isEmpty()) {
            lastFeedTimestampMs = cached.get(cached.size() - 1).openTime().toEpochMilli();
            return cached;
        }

        return fallbackCandles(normalized, timeframe, safeLimit);
    }

    @Override
    public OrderBookSnapshot getLatestOrderBook(String symbol) {
        String normalized = normalizeSymbol(symbol);
        OrderBookSnapshot live = realtimeCache.getOrderBook(normalized);
        if (live != null && !live.bids().isEmpty() && !live.asks().isEmpty()) {
            lastFeedTimestampMs = live.snapshotTime().toEpochMilli();
            return live;
        }

        double mid = resolveMidPrice(normalized);
        double spreadHalf = Math.max(mid * 0.00008, 0.01);
        Instant now = Instant.now();
        lastFeedTimestampMs = now.toEpochMilli();

        return new OrderBookSnapshot(
                normalized,
                now,
                List.of(new OrderBookLevel(mid - spreadHalf, 1.0)),
                List.of(new OrderBookLevel(mid + spreadHalf, 1.0))
        );
    }

    @Override
    public FundingRate getLatestFundingRate(String symbol) {
        return new FundingRate(normalizeSymbol(symbol), Instant.now(), 0.0);
    }

    @Override
    public List<OpenInterestSnapshot> getRecentOpenInterest(String symbol, int limit) {
        String normalized = normalizeSymbol(symbol);
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<OpenInterestSnapshot> points = new ArrayList<>(safeLimit);
        Instant now = Instant.now();
        for (int i = 0; i < safeLimit; i++) {
            points.add(new OpenInterestSnapshot(normalized, now.minusSeconds((long) (safeLimit - i) * 300L), 0.0));
        }
        return points;
    }

    @Override
    public List<LiquidationEvent> getRecentLiquidations(String symbol, int limit) {
        String normalized = normalizeSymbol(symbol);
        int safeLimit = Math.max(1, Math.min(limit, 500));

        List<KrakenRealtimeCache.TradeTick> trades = realtimeCache.recentTrades(normalized, safeLimit);
        if (!trades.isEmpty()) {
            List<LiquidationEvent> events = new ArrayList<>(trades.size());
            for (KrakenRealtimeCache.TradeTick trade : trades) {
                LiquidationSide side = "SELL".equalsIgnoreCase(trade.side()) ? LiquidationSide.LONG : LiquidationSide.SHORT;
                events.add(new LiquidationEvent(
                        normalized,
                        trade.timestamp(),
                        side,
                        trade.price(),
                        Math.max(0.0, trade.price() * trade.quantity())
                ));
            }
            return events;
        }

        List<LiquidationEvent> fallback = new ArrayList<>(safeLimit);
        Instant now = Instant.now();
        for (int i = 0; i < safeLimit; i++) {
            LiquidationSide side = (i % 2 == 0) ? LiquidationSide.LONG : LiquidationSide.SHORT;
            fallback.add(new LiquidationEvent(normalized, now.minusSeconds(i * 60L), side, 0.0, 0.0));
        }
        return fallback;
    }

    @Override
    public FeedStatus getFeedStatus(String symbol) {
        String normalized = normalizeSymbol(symbol);

        Instant latestRealtime = realtimeCache.latestEventTime(normalized);
        if (latestRealtime != null) {
            lastFeedTimestampMs = Math.max(lastFeedTimestampMs, latestRealtime.toEpochMilli());
        }

        if (candleCache.hasData(normalized, Timeframe.M1)) {
            List<Candle> latest = candleCache.getCandles(normalized, Timeframe.M1, 1);
            if (!latest.isEmpty()) {
                lastFeedTimestampMs = Math.max(lastFeedTimestampMs, latest.get(0).openTime().toEpochMilli());
            }
        }

        long ageSeconds = lastFeedTimestampMs == 0L
                ? Long.MAX_VALUE
                : Math.max(0L, (Instant.now().toEpochMilli() - lastFeedTimestampMs) / 1000L);

        return new FeedStatus(lastLatencyMs, ageSeconds);
    }

    private String normalizeSymbol(String symbol) {
        String normalized = String.valueOf(symbol).trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return krakenProperties.getDefaultSymbol();
        }
        return normalized;
    }

    private double resolveMidPrice(String symbol) {
        List<Candle> latest = candleCache.getCandles(symbol, Timeframe.M1, 1);
        if (!latest.isEmpty()) {
            return Math.max(1.0, latest.get(0).close());
        }
        return symbol.startsWith("BTC") ? 65000.0 : symbol.startsWith("ETH") ? 3000.0 : 180.0;
    }

    private List<Candle> fallbackCandles(String symbol, Timeframe timeframe, int limit) {
        double basePrice = symbol.startsWith("BTC") ? 65000.0 : symbol.startsWith("ETH") ? 3000.0 : 180.0;
        Instant now = Instant.now();
        List<Candle> candles = new ArrayList<>(limit);
        double price = basePrice;
        for (int i = limit - 1; i >= 0; i--) {
            Instant openTime = now.minus(timeframe.getDuration().multipliedBy(i + 1L));
            double drift = 1.0 + (((i % 5) - 2) * 0.0006);
            double close = Math.max(1.0, price * drift);
            double high = Math.max(price, close) * 1.0005;
            double low = Math.min(price, close) * 0.9995;
            candles.add(new Candle(symbol, timeframe, openTime, price, high, low, close, 1.0));
            price = close;
        }
        return candles;
    }
}

