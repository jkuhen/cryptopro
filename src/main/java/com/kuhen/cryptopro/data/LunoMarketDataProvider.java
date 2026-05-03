package com.kuhen.cryptopro.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuhen.cryptopro.config.LunoProperties;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.FeedStatus;
import com.kuhen.cryptopro.data.model.FundingRate;
import com.kuhen.cryptopro.data.model.LiquidationEvent;
import com.kuhen.cryptopro.data.model.LiquidationSide;
import com.kuhen.cryptopro.data.model.OpenInterestSnapshot;
import com.kuhen.cryptopro.data.model.OrderBookLevel;
import com.kuhen.cryptopro.data.model.OrderBookSnapshot;
import com.kuhen.cryptopro.data.model.Timeframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "cryptopro.data", name = "provider", havingValue = "luno", matchIfMissing = true)
public class LunoMarketDataProvider implements MarketDataProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LunoMarketDataProvider.class);

    private final LunoProperties lunoProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile long lastLatencyMs = 0;
    private volatile long lastFeedTimestampMs = 0;

    public LunoMarketDataProvider(LunoProperties lunoProperties, ObjectMapper objectMapper) {
        this.lunoProperties = lunoProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().build();
    }

    @Override
    public List<Candle> getRecentCandles(String symbol, Timeframe timeframe, int limit) {
        String pair = mapSymbol(symbol);
        long nowMs = Instant.now().toEpochMilli();
        long sinceMs = nowMs - timeframe.getDuration().toMillis() * Math.max(limit, 1);

        Map<String, String> query = new HashMap<>();
        query.put("pair", pair);
        query.put("since", String.valueOf(sinceMs));
        query.put("duration", String.valueOf(Math.max(60, timeframe.getDuration().toSeconds())));

        try {
            JsonNode root = getJson("/api/exchange/1/candles", query, false);
            List<Candle> candles = parseCandles(root.path("candles"), symbol, timeframe, limit);
            if (!candles.isEmpty()) {
                return candles;
            }
        } catch (Exception ex) {
            LOGGER.warn("Unable to load candles from Luno API for symbol {}. Falling back to ticker-derived candles.", symbol);
        }

        return fallbackCandlesFromTicker(symbol, pair, timeframe, limit);
    }

    @Override
    public OrderBookSnapshot getLatestOrderBook(String symbol) {
        String pair = mapSymbol(symbol);
        Map<String, String> query = Map.of("pair", pair);

        try {
            JsonNode root = getJson("/api/1/orderbook", query, false);
            List<OrderBookLevel> bids = parseLevels(root.path("bids"));
            List<OrderBookLevel> asks = parseLevels(root.path("asks"));
            long timestampMs = root.path("timestamp").asLong(Instant.now().toEpochMilli());
            lastFeedTimestampMs = timestampMs;
            return new OrderBookSnapshot(symbol, Instant.ofEpochMilli(timestampMs), bids, asks);
        } catch (Exception ex) {
            LOGGER.warn("Unable to load Luno order book for symbol {}. Falling back to synthetic top-of-book.", symbol);
            double mid = resolveLastTradePrice(pair);
            double spreadHalf = Math.max(mid * 0.0001, 0.01);
            lastFeedTimestampMs = Instant.now().toEpochMilli();
            return new OrderBookSnapshot(
                    symbol,
                    Instant.now(),
                    List.of(new OrderBookLevel(mid - spreadHalf, 1.0)),
                    List.of(new OrderBookLevel(mid + spreadHalf, 1.0))
            );
        }
    }

    @Override
    public FundingRate getLatestFundingRate(String symbol) {
        return new FundingRate(symbol, Instant.now(), 0.0);
    }

    @Override
    public List<OpenInterestSnapshot> getRecentOpenInterest(String symbol, int limit) {
        List<OpenInterestSnapshot> points = new ArrayList<>(limit);
        Instant now = Instant.now();
        for (int i = 0; i < limit; i++) {
            points.add(new OpenInterestSnapshot(symbol, now.minusSeconds((long) (limit - i) * 300L), 0.0));
        }
        return points;
    }

    @Override
    public List<LiquidationEvent> getRecentLiquidations(String symbol, int limit) {
        List<LiquidationEvent> events = new ArrayList<>(limit);
        Instant now = Instant.now();
        for (int i = 0; i < limit; i++) {
            LiquidationSide side = (i % 2 == 0) ? LiquidationSide.LONG : LiquidationSide.SHORT;
            events.add(new LiquidationEvent(symbol, now.minusSeconds(i * 60L), side, 0.0, 0.0));
        }
        return events;
    }

    @Override
    public FeedStatus getFeedStatus(String symbol) {
        long ageSeconds = lastFeedTimestampMs == 0
                ? Long.MAX_VALUE
                : Math.max(0L, (Instant.now().toEpochMilli() - lastFeedTimestampMs) / 1000L);
        return new FeedStatus(lastLatencyMs, ageSeconds);
    }

    private JsonNode getJson(String path, Map<String, String> queryParams, boolean authRequired) throws IOException, InterruptedException {
        StringBuilder uriBuilder = new StringBuilder(lunoProperties.getBaseUrl()).append(path);
        if (!queryParams.isEmpty()) {
            uriBuilder.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (!first) {
                    uriBuilder.append("&");
                }
                first = false;
                uriBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                uriBuilder.append("=");
                uriBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(uriBuilder.toString()))
                .GET();

        if (authRequired || hasCredentials()) {
            String authToken = Base64.getEncoder().encodeToString((lunoProperties.getApiKey() + ":" + lunoProperties.getApiSecret())
                    .getBytes(StandardCharsets.UTF_8));
            requestBuilder.header("Authorization", "Basic " + authToken);
        }

        long start = System.nanoTime();
        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        lastLatencyMs = (System.nanoTime() - start) / 1_000_000L;

        if (response.statusCode() >= 400) {
            throw new IOException("Luno API returned HTTP " + response.statusCode() + " for " + path);
        }

        return objectMapper.readTree(response.body());
    }

    private boolean hasCredentials() {
        return !lunoProperties.getApiKey().isBlank() && !lunoProperties.getApiSecret().isBlank();
    }

    private String mapSymbol(String symbol) {
        return lunoProperties.getSymbolMap().getOrDefault(symbol.toUpperCase(), lunoProperties.getDefaultPair());
    }

    private List<Candle> parseCandles(JsonNode candleArray, String symbol, Timeframe timeframe, int limit) {
        if (candleArray == null || !candleArray.isArray()) {
            return List.of();
        }

        List<Candle> candles = new ArrayList<>();
        for (JsonNode node : candleArray) {
            long timestamp = node.path("timestamp").asLong(0L);
            if (timestamp <= 0) {
                continue;
            }

            double open = asDouble(node, "open", "opening_price", "close", "closing_price");
            double high = asDouble(node, "high", "high_price", "close", "closing_price");
            double low = asDouble(node, "low", "low_price", "close", "closing_price");
            double close = asDouble(node, "close", "closing_price", "open", "opening_price");
            double volume = asDouble(node, "volume", "base_volume", "0");
            candles.add(new Candle(symbol, timeframe, Instant.ofEpochMilli(timestamp), open, high, low, close, Math.max(0.0, volume)));
        }

        candles.sort(Comparator.comparing(Candle::openTime));
        if (candles.size() <= limit) {
            return candles;
        }
        return candles.subList(candles.size() - limit, candles.size());
    }

    private List<OrderBookLevel> parseLevels(JsonNode levelsNode) {
        if (levelsNode == null || !levelsNode.isArray()) {
            return List.of();
        }

        List<OrderBookLevel> levels = new ArrayList<>();
        for (JsonNode node : levelsNode) {
            double price = asDouble(node, "price", "0");
            double volume = asDouble(node, "volume", "size", "0");
            if (price > 0.0 && volume > 0.0) {
                levels.add(new OrderBookLevel(price, volume));
            }
            if (levels.size() >= 10) {
                break;
            }
        }
        return levels;
    }

    private List<Candle> fallbackCandlesFromTicker(String symbol, String pair, Timeframe timeframe, int limit) {
        double last = resolveLastTradePrice(pair);
        Instant now = Instant.now();
        List<Candle> candles = new ArrayList<>(limit);

        double price = Math.max(last, 1.0);
        for (int i = limit - 1; i >= 0; i--) {
            Instant openTime = now.minus(timeframe.getDuration().multipliedBy(i + 1L));
            double drift = 1.0 + (((i % 5) - 2) * 0.0007);
            double close = Math.max(1.0, price * drift);
            double high = Math.max(price, close) * 1.0005;
            double low = Math.min(price, close) * 0.9995;
            candles.add(new Candle(symbol, timeframe, openTime, price, high, low, close, 1.0));
            price = close;
        }
        return candles;
    }

    private double resolveLastTradePrice(String pair) {
        try {
            JsonNode root = getJson("/api/1/ticker", Map.of("pair", pair), false);
            return asDouble(root, "last_trade", "last_trade_price", "bid", "ask");
        } catch (Exception ex) {
            return 100_000.0;
        }
    }

    private double asDouble(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode valueNode = node.path(field);
            if (!valueNode.isMissingNode() && !valueNode.isNull()) {
                if (valueNode.isNumber()) {
                    return valueNode.asDouble();
                }
                try {
                    return Double.parseDouble(valueNode.asText());
                } catch (NumberFormatException ignored) {
                    // Move to next field.
                }
            }
        }
        return 0.0;
    }
}

