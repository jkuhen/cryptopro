package com.kuhen.cryptopro.data;

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
import java.util.Random;

@Service
@ConditionalOnProperty(prefix = "cryptopro.data", name = "provider", havingValue = "mock")
public class MockMarketDataProvider implements MarketDataProvider {

    @Override
    public List<Candle> getRecentCandles(String symbol, Timeframe timeframe, int limit) {
        Random random = new Random((long) symbol.hashCode() + timeframe.ordinal() * 97L + limit);
        double basePrice = symbol.startsWith("BTC") ? 65000.0 : 3000.0;
        double intervalVol = switch (timeframe) {
            case H1 -> 0.007;
            case M15 -> 0.004;
            case M5 -> 0.0025;
            case M1 -> 0.0015;
        };

        List<Candle> candles = new ArrayList<>(limit);
        Instant end = Instant.now();
        Instant start = end.minus(timeframe.getDuration().multipliedBy(limit));

        double lastClose = basePrice;
        for (int i = 0; i < limit; i++) {
            Instant openTime = start.plus(timeframe.getDuration().multipliedBy(i));
            double drift = 1.0 + (random.nextDouble() - 0.48) * intervalVol;
            double close = Math.max(10.0, lastClose * drift);
            double high = Math.max(close, lastClose) * (1.0 + random.nextDouble() * intervalVol * 0.8);
            double low = Math.min(close, lastClose) * (1.0 - random.nextDouble() * intervalVol * 0.8);
            double volume = 300.0 + random.nextDouble() * 1200.0;
            candles.add(new Candle(symbol, timeframe, openTime, lastClose, high, low, close, volume));
            lastClose = close;
        }

        return candles;
    }

    @Override
    public OrderBookSnapshot getLatestOrderBook(String symbol) {
        double mid = symbol.startsWith("BTC") ? 65000.0 : 3000.0;
        double spreadHalf = symbol.startsWith("BTC") ? 1.5 : 0.15;
        List<OrderBookLevel> bids = List.of(
                new OrderBookLevel(mid - spreadHalf, 7.8),
                new OrderBookLevel(mid - spreadHalf - 0.5, 6.2),
                new OrderBookLevel(mid - spreadHalf - 1.0, 5.1)
        );
        List<OrderBookLevel> asks = List.of(
                new OrderBookLevel(mid + spreadHalf, 6.8),
                new OrderBookLevel(mid + spreadHalf + 0.5, 5.9),
                new OrderBookLevel(mid + spreadHalf + 1.0, 5.0)
        );
        return new OrderBookSnapshot(symbol, Instant.now(), bids, asks);
    }

    @Override
    public FundingRate getLatestFundingRate(String symbol) {
        double rate = symbol.startsWith("BTC") ? 0.0065 : -0.0030;
        return new FundingRate(symbol, Instant.now(), rate);
    }

    @Override
    public List<OpenInterestSnapshot> getRecentOpenInterest(String symbol, int limit) {
        List<OpenInterestSnapshot> points = new ArrayList<>(limit);
        double base = symbol.startsWith("BTC") ? 1_500_000_000.0 : 520_000_000.0;
        Instant now = Instant.now();
        for (int i = 0; i < limit; i++) {
            double growth = 1.0 + (i * 0.0035);
            points.add(new OpenInterestSnapshot(symbol, now.minusSeconds((long) (limit - i) * 300L), base * growth));
        }
        return points;
    }

    @Override
    public List<LiquidationEvent> getRecentLiquidations(String symbol, int limit) {
        List<LiquidationEvent> events = new ArrayList<>(limit);
        Instant now = Instant.now();
        for (int i = 0; i < limit; i++) {
            LiquidationSide side = (i % 3 == 0) ? LiquidationSide.SHORT : LiquidationSide.LONG;
            double size = side == LiquidationSide.SHORT ? 140_000.0 + i * 1000.0 : 95_000.0 + i * 800.0;
            events.add(new LiquidationEvent(symbol, now.minusSeconds(i * 60L), side, 65000.0 + i, size));
        }
        return events;
    }

    @Override
    public FeedStatus getFeedStatus(String symbol) {
        return new FeedStatus(90, 1);
    }
}


