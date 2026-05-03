package com.kuhen.cryptopro.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuhen.cryptopro.config.BinanceProperties;
import com.kuhen.cryptopro.data.dto.BinanceKlineEventDto;
import com.kuhen.cryptopro.data.dto.BinanceKlinePayloadDto;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.Timeframe;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Maintains a persistent WebSocket connection to the Binance combined kline stream
 * for M1 candles of all configured symbols (default: BTCUSDT, ETHUSDT, SOLUSDT).
 *
 * <p>Received kline ticks are written into {@link CandleCache} so that
 * {@link BinanceMarketDataProvider} can serve live data without polling REST.
 *
 * <p>The client reconnects automatically after any disconnection or error.
 */
@Service
@ConditionalOnProperty(prefix = "cryptopro.data", name = "provider", havingValue = "binance")
public class BinanceCandleWebSocketClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(BinanceCandleWebSocketClient.class);
    private static final long RECONNECT_DELAY_SECONDS = 5;

    private final BinanceProperties props;
    private final ObjectMapper mapper;
    private final CandleCache cache;
    private final CandlePersistenceService persistenceService;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    private final AtomicReference<WebSocket> activeSocket = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile List<String> subscribedSymbols;

    /** Default constructor for framework instantiation. */
    public BinanceCandleWebSocketClient() {
        this.props = null;
        this.mapper = null;
        this.cache = null;
        this.persistenceService = null;
        this.httpClient = HttpClient.newBuilder().build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "binance-ws-reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    @Autowired
    public BinanceCandleWebSocketClient(BinanceProperties props,
                                        ObjectMapper mapper,
                                        CandleCache cache,
                                        CandlePersistenceService persistenceService) {
        this.props = props;
        this.mapper = mapper;
        this.cache = cache;
        this.persistenceService = persistenceService;
        this.httpClient = buildHttpClient(props);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "binance-ws-reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @PostConstruct
    public void start() {
        if (!props.isWebsocketEnabled()) {
            LOGGER.info("Binance WebSocket feed is disabled via cryptopro.binance.websocket-enabled=false.");
            return;
        }
        List<String> symbols = props.getWebsocketSymbols();
        if (symbols == null || symbols.isEmpty()) {
            LOGGER.warn("cryptopro.binance.websocket-symbols is empty – skipping WebSocket connection.");
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
        LOGGER.info("Binance WebSocket client stopped.");
    }

    // -------------------------------------------------------------------------
    // Connection management
    // -------------------------------------------------------------------------

    private void connect(List<String> symbols) {
        if (!running.get()) return;

        String streams = symbols.stream()
                .map(s -> s.toLowerCase() + "@kline_1m")
                .collect(Collectors.joining("/"));
        String url = props.getWebsocketUrl() + "/stream?streams=" + streams;

        LOGGER.info("Connecting to Binance WebSocket combined stream: {}", url);

        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(url), new KlineListener(symbols))
                .whenComplete((ws, ex) -> {
                    if (ex != null) {
                        LOGGER.warn("Failed to connect to Binance WebSocket ({}). Retrying in {}s.",
                                ex.getMessage(), RECONNECT_DELAY_SECONDS);
                        scheduleReconnect(symbols);
                    } else {
                        activeSocket.set(ws);
                        LOGGER.info("Binance WebSocket connected. Subscribed to M1 klines: {}", symbols);
                    }
                });
    }

    private void scheduleReconnect(List<String> symbols) {
        if (!running.get()) return;
        scheduler.schedule(() -> connect(symbols), RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // WebSocket listener
    // -------------------------------------------------------------------------

    private class KlineListener implements WebSocket.Listener {

        private final List<String> symbols;
        /** Accumulates partial frames until {@code last == true}. */
        private final StringBuilder buffer = new StringBuilder();

        KlineListener(List<String> symbols) {
            this.symbols = symbols;
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
            LOGGER.warn("Binance WebSocket closed (code={}, reason='{}').", statusCode, reason);
            activeSocket.set(null);
            scheduleReconnect(symbols);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            LOGGER.warn("Binance WebSocket error: {}. Reconnecting in {}s.",
                    error.getMessage(), RECONNECT_DELAY_SECONDS);
            activeSocket.set(null);
            scheduleReconnect(symbols);
        }
    }

    // -------------------------------------------------------------------------
    // Message parsing
    // -------------------------------------------------------------------------

    private void processMessage(String raw) {
        try {
            JsonNode root = mapper.readTree(raw);

            // Combined-stream envelope: {"stream":"btcusdt@kline_1m","data":{...}}
            JsonNode data = root.path("data");
            if (data.isMissingNode()) {
                // Single-stream format (no envelope) – use root directly
                data = root;
            }

            BinanceKlineEventDto event = mapper.treeToValue(data, BinanceKlineEventDto.class);
            if (!event.isKlineEvent()) {
                return; // Ignore non-kline events (e.g. subscription confirmations)
            }

            BinanceKlinePayloadDto kline = event.kline();
            Candle candle = kline.toCandle(Timeframe.M1);

            cache.updateLive(candle);

            if (kline.closed()) {
                LOGGER.debug("M1 candle closed  {} O={} H={} L={} C={} V={}",
                        candle.symbol(), candle.open(), candle.high(), candle.low(), candle.close(), candle.volume());
                persistenceService.saveClosedCandle(candle);
            }
        } catch (Exception ex) {
            LOGGER.warn("Error processing WebSocket message: {}", ex.getMessage());
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
            LOGGER.warn("Binance proxy is enabled but host/port is invalid. Falling back to direct WebSocket client.");
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
            public void connectFailed(URI uri, SocketAddress sa, java.io.IOException ioe) {
                LOGGER.warn("Binance WebSocket proxy connect failed for {} via {}:{}", uri, host, port, ioe);
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

        LOGGER.info("Binance WebSocket HttpClient proxy enabled via {}:{} ({})", host, port, type);
        return builder.build();
    }
}
