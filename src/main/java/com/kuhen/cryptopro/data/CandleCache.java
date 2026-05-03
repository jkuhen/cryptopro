package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.Timeframe;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe rolling buffer of {@link Candle} objects keyed by (symbol, timeframe).
 *
 * <p>The last entry in each deque always represents the <em>current live</em> candle being
 * built from WebSocket ticks.  When a candle's {@code x} (kline-closed) flag is {@code true}
 * the same update is applied; the entry simply stops being replaced on subsequent ticks.
 */
@Component
public class CandleCache {

    /** Maximum number of candles retained per (symbol, timeframe) key. */
    private static final int MAX_CANDLES = 500;

    private final Map<String, Deque<Candle>> store = new ConcurrentHashMap<>();

    /**
     * Update (or insert) the current live candle.
     *
     * <p>If the last stored candle shares the same {@code openTime} as the incoming
     * candle it is replaced in-place; otherwise the candle is appended.  The deque is
     * capped at {@link #MAX_CANDLES}.
     *
     * @param candle the latest candle tick received from the WebSocket stream
     */
    public synchronized void updateLive(Candle candle) {
        Deque<Candle> deque = store.computeIfAbsent(key(candle), k -> new ArrayDeque<>());

        if (!deque.isEmpty() && deque.peekLast().openTime().equals(candle.openTime())) {
            deque.pollLast();
        }
        deque.addLast(candle);

        while (deque.size() > MAX_CANDLES) {
            deque.pollFirst();
        }
    }

    /**
     * Return up to {@code limit} of the most recent candles for the given pair.
     * Returns an empty list when no data is available yet.
     */
    public synchronized List<Candle> getCandles(String symbol, Timeframe timeframe, int limit) {
        Deque<Candle> deque = store.get(symbol + "|" + timeframe.name());
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }
        List<Candle> all = new ArrayList<>(deque);
        int from = Math.max(0, all.size() - limit);
        return List.copyOf(all.subList(from, all.size()));
    }

    /** Returns {@code true} if there is at least one cached candle for this pair. */
    public synchronized boolean hasData(String symbol, Timeframe timeframe) {
        Deque<Candle> deque = store.get(symbol + "|" + timeframe.name());
        return deque != null && !deque.isEmpty();
    }

    /** Returns the number of cached candles for this pair. */
    public synchronized int size(String symbol, Timeframe timeframe) {
        Deque<Candle> deque = store.get(symbol + "|" + timeframe.name());
        return deque == null ? 0 : deque.size();
    }

    private static String key(Candle candle) {
        return candle.symbol() + "|" + candle.timeframe().name();
    }
}

