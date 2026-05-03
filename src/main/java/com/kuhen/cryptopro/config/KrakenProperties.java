package com.kuhen.cryptopro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "cryptopro.kraken")
public class KrakenProperties {

    private String websocketUrl = "wss://ws.kraken.com/v2";
    private String defaultSymbol = "BTCUSDT";
    private Map<String, String> symbolMap = new HashMap<>();
    private boolean websocketEnabled = true;
    private List<String> websocketSymbols = new ArrayList<>(List.of("BTCUSDT", "ETHUSDT", "SOLUSDT"));
    private int bookDepth = 10;
    private int tradeCacheSize = 300;

    public String getWebsocketUrl() {
        return websocketUrl;
    }

    public void setWebsocketUrl(String websocketUrl) {
        this.websocketUrl = websocketUrl;
    }

    public String getDefaultSymbol() {
        return defaultSymbol;
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

    public int getBookDepth() {
        return bookDepth;
    }

    public void setBookDepth(int bookDepth) {
        this.bookDepth = bookDepth;
    }

    public int getTradeCacheSize() {
        return tradeCacheSize;
    }

    public void setTradeCacheSize(int tradeCacheSize) {
        this.tradeCacheSize = tradeCacheSize;
    }
}
