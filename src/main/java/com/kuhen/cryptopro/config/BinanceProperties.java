package com.kuhen.cryptopro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "cryptopro.binance")
public class BinanceProperties {

    private String baseUrl = "https://api.binance.com";
    private String futuresBaseUrl = "https://fapi.binance.com";
    private String defaultSymbol = "BTCUSDT";
    private Map<String, String> symbolMap = new HashMap<>();

    /** Base URL for Binance WebSocket streams, e.g. {@code wss://stream.binance.com:9443}. */
    private String websocketUrl = "wss://stream.binance.com:9443";

    /** Period used when requesting historical open-interest points from Binance futures API. */
    private String openInterestPeriod = "5m";

    /** Set to {@code false} to disable the live WebSocket feed (REST-only mode). */
    private boolean websocketEnabled = true;

    /** Symbols to subscribe to on the M1 kline WebSocket stream. */
    private List<String> websocketSymbols = new ArrayList<>(List.of("BTCUSDT", "ETHUSDT", "SOLUSDT"));

    /** Enables the scheduled candle sync / aggregation job. */
    private boolean syncEnabled = true;

    /** Cron used by the scheduled sync job. Defaults to every minute on the minute. */
    private String syncCron = "0 * * * * *";

    /** Number of recent M1 candles to inspect on each sync cycle. */
    private int syncLookbackCandles = 180;

    /** Maximum number of retry attempts for REST fallback calls. */
    private int restMaxRetries = 3;

    /** Base exponential backoff in milliseconds for REST retry / rate-limit handling. */
    private long restRetryBackoffMs = 1000L;

    /** Enables outbound proxy for Binance REST and WebSocket clients. */
    private boolean proxyEnabled = false;

    /** Proxy hostname or IP address. */
    private String proxyHost = "";

    /** Proxy TCP port. */
    private int proxyPort = 0;

    /** Optional proxy mode: HTTP (default) or SOCKS. */
    private String proxyType = "HTTP";

    /** Optional proxy username (basic auth). */
    private String proxyUsername = "";

    /** Optional proxy password (basic auth). */
    private String proxyPassword = "";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getDefaultSymbol() {
        return defaultSymbol;
    }

    public String getFuturesBaseUrl() {
        return futuresBaseUrl;
    }

    public void setFuturesBaseUrl(String futuresBaseUrl) {
        this.futuresBaseUrl = futuresBaseUrl;
    }

    public void setDefaultSymbol(String defaultSymbol) {
        this.defaultSymbol = defaultSymbol;
    }

    public Map<String, String> getSymbolMap() {
        return symbolMap;
    }

    public void setSymbolMap(Map<String, String> symbolMap) {
        this.symbolMap = symbolMap;
    }

    public String getWebsocketUrl() {
        return websocketUrl;
    }

    public void setWebsocketUrl(String websocketUrl) {
        this.websocketUrl = websocketUrl;
    }

    public boolean isWebsocketEnabled() {
        return websocketEnabled;
    }

    public void setWebsocketEnabled(boolean websocketEnabled) {
        this.websocketEnabled = websocketEnabled;
    }

    public List<String> getWebsocketSymbols() {
        return websocketSymbols;
    }

    public void setWebsocketSymbols(List<String> websocketSymbols) {
        this.websocketSymbols = websocketSymbols;
    }

    public String getOpenInterestPeriod() {
        return openInterestPeriod;
    }

    public void setOpenInterestPeriod(String openInterestPeriod) {
        this.openInterestPeriod = openInterestPeriod;
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    public String getSyncCron() {
        return syncCron;
    }

    public void setSyncCron(String syncCron) {
        this.syncCron = syncCron;
    }

    public int getSyncLookbackCandles() {
        return syncLookbackCandles;
    }

    public void setSyncLookbackCandles(int syncLookbackCandles) {
        this.syncLookbackCandles = syncLookbackCandles;
    }

    public int getRestMaxRetries() {
        return restMaxRetries;
    }

    public void setRestMaxRetries(int restMaxRetries) {
        this.restMaxRetries = restMaxRetries;
    }

    public long getRestRetryBackoffMs() {
        return restRetryBackoffMs;
    }

    public void setRestRetryBackoffMs(long restRetryBackoffMs) {
        this.restRetryBackoffMs = restRetryBackoffMs;
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public void setProxyEnabled(boolean proxyEnabled) {
        this.proxyEnabled = proxyEnabled;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyType() {
        return proxyType;
    }

    public void setProxyType(String proxyType) {
        this.proxyType = proxyType;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }
}
