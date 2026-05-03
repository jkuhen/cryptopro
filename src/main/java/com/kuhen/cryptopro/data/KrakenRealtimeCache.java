package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.config.KrakenProperties;
import com.kuhen.cryptopro.data.model.OrderBookLevel;
import com.kuhen.cryptopro.data.model.OrderBookSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores Kraken WebSocket real-time state for book + trade channels.
 */
@Component
public class KrakenRealtimeCache {

    private final int maxTradesPerSymbol;

    private final Map<String, OrderBookSnapshot> orderBooks = new ConcurrentHashMap<>();
    private final Map<String, Deque<TradeTick>> trades = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastEventTime = new ConcurrentHashMap<>();

    public KrakenRealtimeCache(KrakenProperties krakenProperties) {
        this.maxTradesPerSymbol = Math.max(50, krakenProperties.getTradeCacheSize());
    }

    public void updateOrderBook(String symbol, Instant timestamp,
                                List<OrderBookLevel> bids,
                                List<OrderBookLevel> asks) {
        String key = normalize(symbol);
        List<OrderBookLevel> sortedBids = new ArrayList<>(bids == null ? List.of() : bids);
        List<OrderBookLevel> sortedAsks = new ArrayList<>(asks == null ? List.of() : asks);

        sortedBids.sort(Comparator.comparingDouble(OrderBookLevel::price).reversed());
        sortedAsks.sort(Comparator.comparingDouble(OrderBookLevel::price));

        orderBooks.put(key, new OrderBookSnapshot(key, timestamp, List.copyOf(sortedBids), List.copyOf(sortedAsks)));
        touch(key, timestamp);
    }

    public OrderBookSnapshot getOrderBook(String symbol) {
        return orderBooks.get(normalize(symbol));
    }

    public synchronized void appendTrade(String symbol, Instant timestamp, double price, double quantity, String side) {
        String key = normalize(symbol);
        if (price <= 0.0 || quantity <= 0.0) {
            return;
        }

        Deque<TradeTick> deque = trades.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        deque.addFirst(new TradeTick(key, timestamp, price, quantity, side == null ? "" : side.toUpperCase()));
        while (deque.size() > maxTradesPerSymbol) {
            deque.removeLast();
        }
        touch(key, timestamp);
    }

    public synchronized List<TradeTick> recentTrades(String symbol, int limit) {
        String key = normalize(symbol);
        Deque<TradeTick> deque = trades.get(key);
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }

        int safeLimit = Math.max(1, Math.min(limit, maxTradesPerSymbol));
        List<TradeTick> out = new ArrayList<>(safeLimit);
        int count = 0;
        for (TradeTick trade : deque) {
            if (count++ >= safeLimit) {
                break;
            }
            out.add(trade);
        }
        return out;
    }

    public Instant latestEventTime(String symbol) {
        return lastEventTime.get(normalize(symbol));
    }

    private void touch(String symbol, Instant ts) {
        if (ts == null) {
            return;
        }
        lastEventTime.put(normalize(symbol), ts);
    }

    private String normalize(String symbol) {
        return String.valueOf(symbol).trim().toUpperCase();
    }

    public record TradeTick(String symbol, Instant timestamp, double price, double quantity, String side) {
    }
}
