package com.kuhen.cryptopro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "cryptopro.luno")
public class LunoProperties {

    private String baseUrl = "https://api.luno.com";
    private String apiKey = "";
    private String apiSecret = "";
    private boolean signedRequestsEnabled = true;
    private String signatureHeader = "X-LUNO-SIGNATURE";
    private String nonceHeader = "X-LUNO-NONCE";
    private String defaultPair = "BTCUSDT";
    private Map<String, String> symbolMap = new HashMap<>();
    private boolean futuresEnabled = false;
    private String futuresBaseUrl = "https://api.luno.com";
    private String defaultFuturesPair = "BTCUSDT-PERP";
    private Map<String, String> futuresSymbolMap = new HashMap<>();
    private String futuresMarketOrderPath = "/api/2/futures/marketorder";
    private String futuresLimitOrderPath = "/api/2/futures/postorder";
    private String futuresOrderStatusPathTemplate = "/api/2/futures/orders/{orderId}";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public boolean isSignedRequestsEnabled() {
        return signedRequestsEnabled;
    }

    public void setSignedRequestsEnabled(boolean signedRequestsEnabled) {
        this.signedRequestsEnabled = signedRequestsEnabled;
    }

    public String getSignatureHeader() {
        return signatureHeader;
    }

    public void setSignatureHeader(String signatureHeader) {
        this.signatureHeader = signatureHeader;
    }

    public String getNonceHeader() {
        return nonceHeader;
    }

    public void setNonceHeader(String nonceHeader) {
        this.nonceHeader = nonceHeader;
    }

    public String getDefaultPair() {
        return defaultPair;
    }

    public void setDefaultPair(String defaultPair) {
        this.defaultPair = defaultPair;
    }

    public Map<String, String> getSymbolMap() {
        return symbolMap;
    }

    public void setSymbolMap(Map<String, String> symbolMap) {
        this.symbolMap = symbolMap;
    }

    public boolean isFuturesEnabled() {
        return futuresEnabled;
    }

    public void setFuturesEnabled(boolean futuresEnabled) {
        this.futuresEnabled = futuresEnabled;
    }

    public String getFuturesBaseUrl() {
        return futuresBaseUrl;
    }

    public void setFuturesBaseUrl(String futuresBaseUrl) {
        this.futuresBaseUrl = futuresBaseUrl;
    }

    public String getDefaultFuturesPair() {
        return defaultFuturesPair;
    }

    public void setDefaultFuturesPair(String defaultFuturesPair) {
        this.defaultFuturesPair = defaultFuturesPair;
    }

    public Map<String, String> getFuturesSymbolMap() {
        return futuresSymbolMap;
    }

    public void setFuturesSymbolMap(Map<String, String> futuresSymbolMap) {
        this.futuresSymbolMap = futuresSymbolMap;
    }

    public String getFuturesMarketOrderPath() {
        return futuresMarketOrderPath;
    }

    public void setFuturesMarketOrderPath(String futuresMarketOrderPath) {
        this.futuresMarketOrderPath = futuresMarketOrderPath;
    }

    public String getFuturesLimitOrderPath() {
        return futuresLimitOrderPath;
    }

    public void setFuturesLimitOrderPath(String futuresLimitOrderPath) {
        this.futuresLimitOrderPath = futuresLimitOrderPath;
    }

    public String getFuturesOrderStatusPathTemplate() {
        return futuresOrderStatusPathTemplate;
    }

    public void setFuturesOrderStatusPathTemplate(String futuresOrderStatusPathTemplate) {
        this.futuresOrderStatusPathTemplate = futuresOrderStatusPathTemplate;
    }
}

