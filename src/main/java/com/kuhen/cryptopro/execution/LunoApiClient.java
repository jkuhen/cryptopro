package com.kuhen.cryptopro.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuhen.cryptopro.config.ExecutionProperties;
import com.kuhen.cryptopro.config.LunoProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.StringJoiner;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
public class LunoApiClient {

    private final LunoProperties lunoProperties;
    private final ExecutionProperties executionProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public LunoApiClient(
            LunoProperties lunoProperties,
            ExecutionProperties executionProperties,
            ObjectMapper objectMapper
    ) {
        this(lunoProperties, executionProperties, objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(Math.max(1000, executionProperties.getTimeoutMs())))
                        .build());
    }

    LunoApiClient(
            LunoProperties lunoProperties,
            ExecutionProperties executionProperties,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this.lunoProperties = lunoProperties;
        this.executionProperties = executionProperties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public JsonNode get(String path, Map<String, String> queryParams) throws IOException, InterruptedException {
        return get(lunoProperties.getBaseUrl(), path, queryParams);
    }

    public JsonNode get(String baseUrl, String path, Map<String, String> queryParams) throws IOException, InterruptedException {
        String queryString = buildQueryString(queryParams);
        String nonce = Long.toString(System.currentTimeMillis());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path + queryString))
                .timeout(Duration.ofMillis(Math.max(1000, executionProperties.getTimeoutMs())))
                .header("Authorization", buildAuthorizationHeader())
                .GET();
        applySignatureHeaders(builder, "GET", path, queryString, "", nonce);
        HttpRequest request = builder.build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(path, response);
    }

    public JsonNode postForm(String path, Map<String, String> formBody) throws IOException, InterruptedException {
        return postForm(lunoProperties.getBaseUrl(), path, formBody);
    }

    public JsonNode postForm(String baseUrl, String path, Map<String, String> formBody) throws IOException, InterruptedException {
        String body = toFormBody(formBody);
        String nonce = Long.toString(System.currentTimeMillis());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofMillis(Math.max(1000, executionProperties.getTimeoutMs())))
                .header("Authorization", buildAuthorizationHeader())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        applySignatureHeaders(builder, "POST", path, "", body, nonce);
        HttpRequest request = builder.build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(path, response);
    }

    private String buildAuthorizationHeader() {
        String apiKey = lunoProperties.getApiKey();
        String apiSecret = lunoProperties.getApiSecret();
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalStateException("Missing Luno API credentials");
        }

        String token = Base64.getEncoder()
                .encodeToString((apiKey + ":" + apiSecret).getBytes(StandardCharsets.UTF_8));
        // Luno uses API key/secret as authenticated signature credentials via Basic auth.
        return "Basic " + token;
    }

    private JsonNode parseResponse(String path, HttpResponse<String> response) throws IOException {
        if (response.statusCode() >= 400) {
            throw new IOException("Luno API returned HTTP " + response.statusCode() + " for " + path + ": " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    private String toFormBody(Map<String, String> body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> entry : new TreeMap<>(body).entrySet()) {
            String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
            String value = URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8);
            joiner.add(key + "=" + value);
        }
        return joiner.toString();
    }

    private String buildQueryString(Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&", "?", "");
        for (Map.Entry<String, String> entry : new TreeMap<>(queryParams).entrySet()) {
            String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
            String value = URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8);
            joiner.add(key + "=" + value);
        }
        return joiner.toString();
    }

    private String buildRequestSignature(String method, String path, String queryString, String body, String nonce) {
        if (!lunoProperties.isSignedRequestsEnabled()) {
            return "";
        }
        String payload = method + "\n" + path + "\n" + queryString + "\n" + body + "\n" + nonce;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(lunoProperties.getApiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign Luno request", ex);
        }
    }

    private void applySignatureHeaders(HttpRequest.Builder builder, String method, String path, String queryString, String body, String nonce) {
        if (!lunoProperties.isSignedRequestsEnabled()) {
            return;
        }
        builder.header(lunoProperties.getNonceHeader(), nonce);
        builder.header(lunoProperties.getSignatureHeader(), buildRequestSignature(method, path, queryString, body, nonce));
    }
}

