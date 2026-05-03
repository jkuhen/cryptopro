package com.kuhen.cryptopro.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuhen.cryptopro.config.KrakenProperties;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.OrderBookLevel;
import com.kuhen.cryptopro.data.model.Timeframe;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@ConditionalOnProperty(prefix = "cryptopro.data", name = "provider", havingValue = "kraken")
public class KrakenCandleWebSocketClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(KrakenCandleWebSocketClient.class);
    private static final long RECONNECT_DELAY_SECONDS = 5L;

    private final KrakenProperties props;
    private final ObjectMapper mapper;
    private final CandleCache cache;
    private final KrakenRealtimeCache realtimeCache;
    private final CandlePersistenceService persistenceService;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    private final AtomicReference<WebSocket> activeSocket = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile List<String> subscribedSymbols;

    public KrakenCandleWebSocketClient(
            KrakenProperties props,
            ObjectMapper mapper,
            CandleCache cache,
            KrakenRealtimeCache realtimeCache,
            CandlePersistenceService persistenceService) {
        this.props = props;
        this.mapper = mapper;
        this.cache = cache;
        this.realtimeCache = realtimeCache;
        this.persistenceService = persistenceService;
        this.httpClient = HttpClient.newBuilder().build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kraken-ws-reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void start() {
        if (!props.isWebsocketEnabled()) {
            LOGGER.info("Kraken WebSocket feed is disabled via cryptopro.kraken.websocket-enabled=false.");
            return;
        }

        List<String> symbols = props.getWebsocketSymbols();
        if (symbols == null || symbols.isEmpty()) {
            LOGGER.warn("cryptopro.kraken.websocket-symbols is empty - skipping WebSocket connection.");
            return;
        }

        subscribedSymbols = List.copyOf(symbols);
        running.set(true);
        connect(subscribedSymbols);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        scheduler.shutdownNow();
        WebSocket ws = activeSocket.getAndSet(null);
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown").exceptionally(ex -> null);
        }
        LOGGER.info("Kraken WebSocket client stopped.");
    }

    private void connect(List<String> appSymbols) {
        if (!running.get()) {
            return;
        }

        String url = props.getWebsocketUrl();
        LOGGER.info("Connecting to Kraken WebSocket v2: {}", url);

        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(url), new KrakenListener(appSymbols))
                .whenComplete((ws, ex) -> {
                    if (ex != null) {
                        LOGGER.warn("Failed to connect to Kraken WebSocket ({}). Retrying in {}s.",
                                ex.getMessage(), RECONNECT_DELAY_SECONDS);
                        scheduleReconnect(appSymbols);
                    } else {
                        activeSocket.set(ws);
                        LOGGER.info("Kraken WebSocket connected. Awaiting OHLC/trade/book updates for {}", appSymbols);
                    }
                });
    }

    private void scheduleReconnect(List<String> appSymbols) {
        if (!running.get()) {
            return;
        }
        scheduler.schedule(() -> connect(appSymbols), RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private class KrakenListener implements WebSocket.Listener {

        private final List<String> appSymbols;
        private final StringBuilder buffer = new StringBuilder();

        private KrakenListener(List<String> appSymbols) {
            this.appSymbols = appSymbols;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            webSocket.request(1);
            sendSubscribe(webSocket, appSymbols);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                processMessage(buffer.toString());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            LOGGER.warn("Kraken WebSocket closed (code={}, reason='{}').", statusCode, reason);
            activeSocket.set(null);
            scheduleReconnect(appSymbols);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            LOGGER.warn("Kraken WebSocket error: {}. Reconnecting in {}s.",
                    error.getMessage(), RECONNECT_DELAY_SECONDS);
            activeSocket.set(null);
            scheduleReconnect(appSymbols);
        }
    }

    private void sendSubscribe(WebSocket webSocket, List<String> appSymbols) {
        try {
            List<String> exchangeSymbols = new ArrayList<>();
            for (String appSymbol : appSymbols) {
                exchangeSymbols.add(toExchangeSymbol(appSymbol));
            }

            sendChannelSubscribe(webSocket, "ohlc", exchangeSymbols, Map.of(
                    "interval", 1,
                    "snapshot", true
            ));
            sendChannelSubscribe(webSocket, "trade", exchangeSymbols, Map.of(
                    "snapshot", true
            ));
            sendChannelSubscribe(webSocket, "book", exchangeSymbols, Map.of(
                    "depth", Math.max(1, props.getBookDepth()),
                    "snapshot", true
            ));

            LOGGER.info("Subscribed to Kraken channels [ohlc, trade, book] for symbols={}", exchangeSymbols);
        } catch (Exception ex) {
            LOGGER.warn("Failed to send Kraken subscribe payload", ex);
        }
    }

    private void sendChannelSubscribe(WebSocket webSocket,
                                      String channel,
                                      List<String> symbols,
                                      Map<String, Object> extras) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("channel", channel);
        params.put("symbol", symbols);
        params.putAll(extras);

        Map<String, Object> payload = new HashMap<>();
        payload.put("method", "subscribe");
        payload.put("params", params);

        String json = mapper.writeValueAsString(payload);
        webSocket.sendText(json, true);
    }

    private void processMessage(String raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            String channel = root.path("channel").asText("");
            if (channel.isBlank()) {
                return;
            }

            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return;
            }

            switch (channel.toLowerCase(Locale.ROOT)) {
                case "ohlc" -> processOhlcData(data);
                case "trade" -> processTradeData(data);
                case "book" -> processBookData(data);
                default -> {
                    // Ignore unsupported channels.
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("Error processing Kraken WebSocket message: {}", ex.getMessage());
        }
    }

    private void processOhlcData(JsonNode data) {
        for (JsonNode node : data) {
            Candle candle = toCandle(node);
            if (candle == null) {
                continue;
            }

            cache.updateLive(candle);

            if (isClosed(node)) {
                persistenceService.saveClosedCandle(candle);
            }
        }
    }

    private void processTradeData(JsonNode data) {
        for (JsonNode symbolNode : data) {
            String appSymbol = toAppSymbol(symbolNode.path("symbol").asText(""));
            JsonNode trades = symbolNode.path("trades");

            if (!trades.isArray()) {
                // Some payloads may emit each trade directly in data[]
                if (symbolNode.has("price") && symbolNode.has("qty")) {
                    Instant ts = parseInstant(symbolNode.path("timestamp"), symbolNode.path("time"));
                    String side = symbolNode.path("side").asText("");
                    realtimeCache.appendTrade(appSymbol, ts == null ? Instant.now() : ts,
                            asDouble(symbolNode.path("price")), asDouble(symbolNode.path("qty")), side);
                }
                continue;
            }

            for (JsonNode trade : trades) {
                Instant ts = parseInstant(trade.path("timestamp"), trade.path("time"));
                double price = asDouble(trade.path("price"));
                double qty = asDouble(trade.path("qty"));
                if (qty <= 0.0) {
                    qty = asDouble(trade.path("volume"));
                }
                String side = trade.path("side").asText("");
                realtimeCache.appendTrade(appSymbol, ts == null ? Instant.now() : ts, price, qty, side);
            }
        }
    }

    private void processBookData(JsonNode data) {
        for (JsonNode symbolNode : data) {
            String appSymbol = toAppSymbol(symbolNode.path("symbol").asText(""));
            List<OrderBookLevel> bids = extractLevels(symbolNode.path("bids"));
            List<OrderBookLevel> asks = extractLevels(symbolNode.path("asks"));
            if (bids.isEmpty() || asks.isEmpty()) {
                continue;
            }

            Instant ts = parseInstant(symbolNode.path("timestamp"), symbolNode.path("time"));
            realtimeCache.updateOrderBook(appSymbol, ts == null ? Instant.now() : ts, bids, asks);
        }
    }

    private List<OrderBookLevel> extractLevels(JsonNode levelsNode) {
        if (!levelsNode.isArray()) {
            return List.of();
        }

        int depth = Math.max(1, props.getBookDepth());
        List<OrderBookLevel> levels = new ArrayList<>(depth);
        for (JsonNode levelNode : levelsNode) {
            OrderBookLevel level = toLevel(levelNode);
            if (level != null) {
                levels.add(level);
            }
            if (levels.size() >= depth) {
                break;
            }
        }
        return levels;
    }

    private OrderBookLevel toLevel(JsonNode levelNode) {
        if (levelNode == null || levelNode.isMissingNode() || levelNode.isNull()) {
            return null;
        }

        double price;
        double qty;
        if (levelNode.isArray()) {
            if (levelNode.size() < 2) {
                return null;
            }
            price = asDouble(levelNode.get(0));
            qty = asDouble(levelNode.get(1));
        } else {
            price = asDouble(levelNode.path("price"));
            qty = asDouble(levelNode.path("qty"));
            if (qty <= 0.0) {
                qty = asDouble(levelNode.path("volume"));
            }
        }

        if (price <= 0.0 || qty <= 0.0) {
            return null;
        }
        return new OrderBookLevel(price, qty);
    }

    private Candle toCandle(JsonNode node) {
        String exchangeSymbol = node.path("symbol").asText("");
        String appSymbol = toAppSymbol(exchangeSymbol);

        Instant openTime = parseInstant(
                node.path("interval_begin"),
                node.path("timestamp"),
                node.path("time")
        );
        if (openTime == null) {
            return null;
        }

        double open = asDouble(node.path("open"));
        double high = asDouble(node.path("high"));
        double low = asDouble(node.path("low"));
        double close = asDouble(node.path("close"));
        double volume = asDouble(node.path("volume"));

        if (open <= 0.0 || high <= 0.0 || low <= 0.0 || close <= 0.0) {
            return null;
        }

        return new Candle(appSymbol, Timeframe.M1, openTime, open, high, low, close, Math.max(0.0, volume));
    }

    private boolean isClosed(JsonNode node) {
        return node.path("final").asBoolean(false)
                || node.path("closed").asBoolean(false)
                || node.path("complete").asBoolean(false);
    }

    private Instant parseInstant(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                continue;
            }

            if (node.isNumber()) {
                long value = node.asLong(0L);
                if (value > 0L) {
                    return value > 9_999_999_999L
                            ? Instant.ofEpochMilli(value)
                            : Instant.ofEpochSecond(value);
                }
                continue;
            }

            String text = node.asText("").trim();
            if (text.isEmpty()) {
                continue;
            }

            try {
                return Instant.parse(text);
            } catch (DateTimeParseException ignored) {
                try {
                    long numeric = Long.parseLong(text);
                    return numeric > 9_999_999_999L
                            ? Instant.ofEpochMilli(numeric)
                            : Instant.ofEpochSecond(numeric);
                } catch (NumberFormatException ignoredAgain) {
                    // continue
                }
            }
        }
        return null;
    }

    private double asDouble(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0.0;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        try {
            return Double.parseDouble(node.asText("0"));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private String toExchangeSymbol(String appSymbol) {
        String normalized = String.valueOf(appSymbol).trim().toUpperCase(Locale.ROOT);
        return props.getSymbolMap().getOrDefault(normalized, props.getSymbolMap().getOrDefault(props.getDefaultSymbol(), "BTC/USD"));
    }

    private String toAppSymbol(String exchangeSymbol) {
        String normalized = String.valueOf(exchangeSymbol).trim().toUpperCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : props.getSymbolMap().entrySet()) {
            if (normalized.equals(entry.getValue().toUpperCase(Locale.ROOT))) {
                return entry.getKey().toUpperCase(Locale.ROOT);
            }
        }

        // Generic fallback, e.g. BTC/USD -> BTCUSDT
        return normalized.replace("/", "").replace("USD", "USDT");
    }
}

