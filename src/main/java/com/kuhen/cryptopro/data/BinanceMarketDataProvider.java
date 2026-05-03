package com.kuhen.cryptopro.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuhen.cryptopro.config.BinanceProperties;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "cryptopro.data", name = "provider", havingValue = "binance")
public class BinanceMarketDataProvider implements MarketDataProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(BinanceMarketDataProvider.class);

    private final BinanceProperties binanceProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final CandleCache candleCache;

    private volatile long lastLatencyMs = 0;
    private volatile long lastFeedTimestampMs = 0;

    /** Default constructor for framework instantiation. */
    public BinanceMarketDataProvider() {
        this.binanceProperties = null;
        this.objectMapper = null;
        this.candleCache = null;
        this.httpClient = HttpClient.newBuilder().build();
    }

    @Autowired
    public BinanceMarketDataProvider(BinanceProperties binanceProperties,
                                     ObjectMapper objectMapper,
                                     CandleCache candleCache) {
        this.binanceProperties = binanceProperties;
        this.objectMapper = objectMapper;
        this.candleCache = candleCache;
        this.httpClient = buildHttpClient(binanceProperties);
    }

    @Override
    public List<Candle> getRecentCandles(String symbol, Timeframe timeframe, int limit) {
        List<Candle> exchangeCandles = getRecentCandlesFromExchange(symbol, timeframe, limit);
        if (!exchangeCandles.isEmpty()) {
            return exchangeCandles;
        }

        String pair = mapSymbol(symbol);
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        return fallbackCandles(pair, timeframe, safeLimit);
    }

    /**
     * Returns only live/cache or real Binance exchange candles.
     * Unlike {@link #getRecentCandles(String, Timeframe, int)} this method never returns
     * synthetic fallback candles and is therefore safe for persistence jobs.
     */
    public List<Candle> getRecentCandlesFromExchange(String symbol, Timeframe timeframe, int limit) {
        String pair = mapSymbol(symbol);
        int safeLimit = Math.max(1, Math.min(limit, 1000));

        List<Candle> cached = candleCache.getCandles(pair, timeframe, safeLimit);
        if (cached.size() >= safeLimit) {
            lastFeedTimestampMs = cached.get(cached.size() - 1).openTime().toEpochMilli();
            return cached;
        }

        Map<String, String> query = new HashMap<>();
        query.put("symbol", pair);
        query.put("interval", toBinanceInterval(timeframe));
        query.put("limit", String.valueOf(safeLimit));

        try {
            JsonNode root = getJson("/api/v3/klines", query);
            List<Candle> candles = parseKlines(root, pair, timeframe, safeLimit);
            if (!candles.isEmpty()) {
                candles.forEach(candleCache::updateLive);
                return candles;
            }
        } catch (Exception ex) {
            LOGGER.warn("Unable to load Binance exchange klines for {} {}.", pair, timeframe, ex);
        }

        return cached;
    }

    /**
     * Returns exchange klines using explicit time boundaries for historical paging.
     * This method never falls back to synthetic candles.
     */
    public List<Candle> getExchangeCandles(String symbol,
                                           Timeframe timeframe,
                                           int limit,
                                           Instant startTime,
                                           Instant endTime) {
        String pair = mapSymbol(symbol);
        int safeLimit = Math.max(1, Math.min(limit, 1000));

        Map<String, String> query = new HashMap<>();
        query.put("symbol", pair);
        query.put("interval", toBinanceInterval(timeframe));
        query.put("limit", String.valueOf(safeLimit));
        if (startTime != null) {
            query.put("startTime", String.valueOf(startTime.toEpochMilli()));
        }
        if (endTime != null) {
            query.put("endTime", String.valueOf(endTime.toEpochMilli()));
        }

        try {
            JsonNode root = getJson("/api/v3/klines", query);
            return parseKlines(root, pair, timeframe, safeLimit);
        } catch (Exception ex) {
            LOGGER.warn("Unable to load paged Binance klines for {} {} (start={}, end={}).",
                    pair, timeframe, startTime, endTime, ex);
            return List.of();
        }
    }

    @Override
    public OrderBookSnapshot getLatestOrderBook(String symbol) {
        String pair = mapSymbol(symbol);
        Map<String, String> query = Map.of("symbol", pair, "limit", "10");

        try {
            JsonNode root = getJson("/api/v3/depth", query);
            List<OrderBookLevel> bids = parseDepthLevels(root.path("bids"));
            List<OrderBookLevel> asks = parseDepthLevels(root.path("asks"));
            Instant now = Instant.now();
            lastFeedTimestampMs = now.toEpochMilli();
            return new OrderBookSnapshot(pair, now, bids, asks);
        } catch (Exception ex) {
            LOGGER.warn("Unable to load Binance depth for {}. Falling back to synthetic top-of-book.", pair, ex);
            return syntheticOrderBook(pair);
        }
    }

    @Override
    public FundingRate getLatestFundingRate(String symbol) {
        String pair = mapSymbol(symbol);
        Map<String, String> query = new HashMap<>();
        query.put("symbol", pair);
        query.put("limit", "1");

        try {
            JsonNode root = getJsonFromBaseUrl(binanceProperties.getFuturesBaseUrl(), "/fapi/v1/fundingRate", query);
            if (root != null && root.isArray() && !root.isEmpty()) {
                JsonNode item = root.get(0);
                double rate = asDouble(item.path("fundingRate"));
                long fundingTimeMs = item.path("fundingTime").asLong(0L);
                if (Double.isFinite(rate) && fundingTimeMs > 0L) {
                    return new FundingRate(pair, Instant.ofEpochMilli(fundingTimeMs), rate);
                }
            }
            LOGGER.warn("Funding-rate response for {} was empty or malformed.", pair);
        } catch (Exception ex) {
            LOGGER.warn("Unable to load Binance funding rate for {}.", pair, ex);
        }

        return new FundingRate(pair, Instant.EPOCH, Double.NaN);
    }

    @Override
    public List<OpenInterestSnapshot> getRecentOpenInterest(String symbol, int limit) {
        String pair = mapSymbol(symbol);
        int safeLimit = Math.max(1, Math.min(limit, 500));
        Map<String, String> query = new HashMap<>();
        query.put("symbol", pair);
        query.put("period", binanceProperties.getOpenInterestPeriod());
        query.put("limit", String.valueOf(safeLimit));

        try {
            JsonNode root = getJsonFromBaseUrl(binanceProperties.getFuturesBaseUrl(), "/futures/data/openInterestHist", query);
            if (root == null || !root.isArray()) {
                LOGGER.warn("Open-interest response for {} was not an array.", pair);
                return List.of();
            }

            List<OpenInterestSnapshot> points = new ArrayList<>();
            for (JsonNode item : root) {
                long timestampMs = item.path("timestamp").asLong(0L);
                double oiUsd = asDouble(item.path("sumOpenInterestValue"));
                if (timestampMs <= 0L || !Double.isFinite(oiUsd) || oiUsd < 0.0) {
                    continue;
                }
                points.add(new OpenInterestSnapshot(pair, Instant.ofEpochMilli(timestampMs), oiUsd));
            }
            points.sort(Comparator.comparing(OpenInterestSnapshot::timestamp));
            return points;
        } catch (Exception ex) {
            LOGGER.warn("Unable to load Binance open interest for {}.", pair, ex);
            return List.of();
        }
    }

    @Override
    public List<LiquidationEvent> getRecentLiquidations(String symbol, int limit) {
        List<LiquidationEvent> events = new ArrayList<>(Math.max(1, limit));
        Instant now = Instant.now();
        for (int i = 0; i < Math.max(1, limit); i++) {
            LiquidationSide side = (i % 2 == 0) ? LiquidationSide.LONG : LiquidationSide.SHORT;
            events.add(new LiquidationEvent(mapSymbol(symbol), now.minusSeconds(i * 60L), side, 0.0, 0.0));
        }
        return events;
    }

    @Override
    public FeedStatus getFeedStatus(String symbol) {
        String pair = mapSymbol(symbol);
        // Prefer timestamp from live WebSocket cache when available.
        if (candleCache.hasData(pair, Timeframe.M1)) {
            List<Candle> latest = candleCache.getCandles(pair, Timeframe.M1, 1);
            if (!latest.isEmpty()) {
                lastFeedTimestampMs = latest.get(0).openTime().toEpochMilli();
            }
        }
        long ageSeconds = lastFeedTimestampMs == 0
                ? Long.MAX_VALUE
                : Math.max(0L, (Instant.now().toEpochMilli() - lastFeedTimestampMs) / 1000L);
        return new FeedStatus(lastLatencyMs, ageSeconds);
    }

    private JsonNode getJson(String path, Map<String, String> queryParams) throws IOException, InterruptedException {
        return getJsonFromBaseUrl(binanceProperties.getBaseUrl(), path, queryParams);
    }

    private JsonNode getJsonFromBaseUrl(String baseUrl, String path, Map<String, String> queryParams) throws IOException, InterruptedException {
        StringBuilder uriBuilder = new StringBuilder(baseUrl).append(path);
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uriBuilder.toString()))
                .GET()
                .build();

        return sendJsonWithRetry(request, path);
    }

    private JsonNode sendJsonWithRetry(HttpRequest request, String path) throws IOException, InterruptedException {
        int maxRetries = Math.max(1, binanceProperties.getRestMaxRetries());
        IOException lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            long start = System.nanoTime();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                lastLatencyMs = (System.nanoTime() - start) / 1_000_000L;

                int status = response.statusCode();
                if (status == 429 || status == 418) {
                    long delayMs = resolveRetryDelayMs(response, attempt);
                    if (attempt == maxRetries) {
                        throw new IOException("Binance rate limit HTTP " + status + " for " + path);
                    }
                    LOGGER.warn("Binance rate limited request to {} (HTTP {}). Retrying in {} ms (attempt {}/{}).",
                            path, status, delayMs, attempt, maxRetries);
                    Thread.sleep(delayMs);
                    continue;
                }

                if (status >= 500) {
                    if (attempt == maxRetries) {
                        throw new IOException("Binance API returned HTTP " + status + " for " + path);
                    }
                    long delayMs = backoffDelayMs(attempt);
                    LOGGER.warn("Transient Binance server error {} for {}. Retrying in {} ms (attempt {}/{}).",
                            status, path, delayMs, attempt, maxRetries);
                    Thread.sleep(delayMs);
                    continue;
                }

                if (status >= 400) {
                    throw new IOException("Binance API returned HTTP " + status + " for " + path);
                }

                return objectMapper.readTree(response.body());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            } catch (IOException ex) {
                lastException = ex;
                if (attempt == maxRetries) {
                    break;
                }
                long delayMs = backoffDelayMs(attempt);
                LOGGER.warn("Binance REST call failed for {}. Retrying in {} ms (attempt {}/{}).",
                        path, delayMs, attempt, maxRetries, ex);
                Thread.sleep(delayMs);
            }
        }

        throw lastException == null ? new IOException("Unknown Binance REST failure for " + path) : lastException;
    }

    private long resolveRetryDelayMs(HttpResponse<String> response, int attempt) {
        Optional<Long> retryAfterSeconds = response.headers()
                .firstValue("Retry-After")
                .flatMap(this::parseLongSafely);

        if (retryAfterSeconds.isPresent()) {
            return Math.max(250L, retryAfterSeconds.get() * 1000L);
        }
        return backoffDelayMs(attempt);
    }

    private Optional<Long> parseLongSafely(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private long backoffDelayMs(int attempt) {
        long base = Math.max(100L, binanceProperties.getRestRetryBackoffMs());
        long multiplier = 1L << Math.min(Math.max(0, attempt - 1), 5);
        return Math.min(30_000L, base * multiplier);
    }

    private String toBinanceInterval(Timeframe timeframe) {
        return switch (timeframe) {
            case M1 -> "1m";
            case M5 -> "5m";
            case M15 -> "15m";
            case H1 -> "1h";
        };
    }

    private String mapSymbol(String symbol) {
        String normalized = String.valueOf(symbol).toUpperCase();
        return binanceProperties.getSymbolMap().getOrDefault(normalized, binanceProperties.getDefaultSymbol());
    }

    private List<Candle> parseKlines(JsonNode root, String symbol, Timeframe timeframe, int limit) {
        if (root == null || !root.isArray()) {
            return List.of();
        }

        List<Candle> candles = new ArrayList<>();
        for (JsonNode row : root) {
            if (!row.isArray() || row.size() < 6) {
                continue;
            }

            long openTimeMs = row.path(0).asLong(0L);
            if (openTimeMs <= 0) {
                continue;
            }

            double open = asDouble(row.path(1));
            double high = asDouble(row.path(2));
            double low = asDouble(row.path(3));
            double close = asDouble(row.path(4));
            double volume = asDouble(row.path(5));
            candles.add(new Candle(symbol, timeframe, Instant.ofEpochMilli(openTimeMs), open, high, low, close, volume));
        }

        candles.sort(Comparator.comparing(Candle::openTime));
        if (!candles.isEmpty()) {
            lastFeedTimestampMs = candles.get(candles.size() - 1).openTime().toEpochMilli();
        }

        if (candles.size() <= limit) {
            return candles;
        }
        return candles.subList(candles.size() - limit, candles.size());
    }

    private List<OrderBookLevel> parseDepthLevels(JsonNode levelsNode) {
        if (levelsNode == null || !levelsNode.isArray()) {
            return List.of();
        }

        List<OrderBookLevel> levels = new ArrayList<>();
        for (JsonNode levelNode : levelsNode) {
            if (!levelNode.isArray() || levelNode.size() < 2) {
                continue;
            }
            double price = asDouble(levelNode.path(0));
            double qty = asDouble(levelNode.path(1));
            if (price > 0.0 && qty > 0.0) {
                levels.add(new OrderBookLevel(price, qty));
            }
            if (levels.size() >= 10) {
                break;
            }
        }
        return levels;
    }

    private OrderBookSnapshot syntheticOrderBook(String symbol) {
        double mid = symbol.startsWith("BTC") ? 65000.0 : symbol.startsWith("ETH") ? 3000.0 : 180.0;
        double spreadHalf = Math.max(mid * 0.00005, 0.01);
        Instant now = Instant.now();
        lastFeedTimestampMs = now.toEpochMilli();
        return new OrderBookSnapshot(
                symbol,
                now,
                List.of(new OrderBookLevel(mid - spreadHalf, 1.0)),
                List.of(new OrderBookLevel(mid + spreadHalf, 1.0))
        );
    }

    private List<Candle> fallbackCandles(String symbol, Timeframe timeframe, int limit) {
        double basePrice = symbol.startsWith("BTC") ? 65000.0 : symbol.startsWith("ETH") ? 3000.0 : 180.0;
        Instant now = Instant.now();
        List<Candle> candles = new ArrayList<>(limit);
        double price = basePrice;
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

    private double asDouble(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return 0.0;
        }
        if (valueNode.isNumber()) {
            return valueNode.asDouble();
        }
        try {
            return Double.parseDouble(valueNode.asText());
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private HttpClient buildHttpClient(BinanceProperties properties) {
        HttpClient.Builder builder = HttpClient.newBuilder();
        if (properties == null || !properties.isProxyEnabled()) {
            return builder.build();
        }

        String host = String.valueOf(properties.getProxyHost()).trim();
        int port = properties.getProxyPort();
        if (host.isBlank() || port <= 0) {
            LOGGER.warn("Binance proxy is enabled but host/port is invalid. Falling back to direct client.");
            return builder.build();
        }

        final String proxyType = String.valueOf(properties.getProxyType()).trim().toUpperCase();
        final Proxy.Type type = "SOCKS".equals(proxyType) || "SOCKS5".equals(proxyType)
                ? Proxy.Type.SOCKS
                : Proxy.Type.HTTP;

        builder.proxy(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return List.of(new Proxy(type, new InetSocketAddress(host, port)));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                LOGGER.warn("Binance proxy connect failed for {} via {}:{}", uri, host, port, ioe);
            }
        });

        String username = String.valueOf(properties.getProxyUsername()).trim();
        String password = String.valueOf(properties.getProxyPassword());
        if (!username.isBlank() && !password.isBlank()) {
            // JDK disables Basic for HTTPS proxy tunneling by default; enable it when proxy auth is configured.
            System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
            System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password.toCharArray());
                }
            });
        }

        LOGGER.info("Binance REST/WebSocket HttpClient proxy enabled via {}:{} ({})", host, port, type);
        return builder.build();
    }
}

