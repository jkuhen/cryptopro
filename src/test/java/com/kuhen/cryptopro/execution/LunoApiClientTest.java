package com.kuhen.cryptopro.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuhen.cryptopro.config.ExecutionProperties;
import com.kuhen.cryptopro.config.LunoProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LunoApiClientTest {

    @Test
    void sendsSignedGetRequestUsingConfiguredCredentials() throws Exception {
        LunoProperties properties = baseProperties();
        properties.setSignedRequestsEnabled(true);

        ExecutionProperties execution = new ExecutionProperties();
        HttpClient httpClient = mock(HttpClient.class);

        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        LunoApiClient client = new LunoApiClient(properties, execution, new ObjectMapper(), httpClient);
        client.get("/api/1/balance", Map.of("currency", "XBT"));

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();

        assertTrue(request.headers().firstValue("Authorization").orElseThrow().startsWith("Basic "));
        assertTrue(request.headers().firstValue(properties.getNonceHeader()).isPresent());
        assertTrue(request.headers().firstValue(properties.getSignatureHeader()).isPresent());
    }

    @Test
    void failsFastWhenCredentialsAreMissing() {
        LunoProperties properties = new LunoProperties();
        properties.setBaseUrl("https://api.luno.com");

        ExecutionProperties execution = new ExecutionProperties();
        HttpClient httpClient = mock(HttpClient.class);

        LunoApiClient client = new LunoApiClient(properties, execution, new ObjectMapper(), httpClient);

        assertThrows(IllegalStateException.class, () -> client.get("/api/1/balance", Map.of()));
    }

    @Test
    void omitsSignatureHeadersWhenSigningIsDisabled() throws Exception {
        LunoProperties properties = baseProperties();
        properties.setSignedRequestsEnabled(false);

        ExecutionProperties execution = new ExecutionProperties();
        HttpClient httpClient = mock(HttpClient.class);

        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        LunoApiClient client = new LunoApiClient(properties, execution, new ObjectMapper(), httpClient);
        client.postForm("/api/1/postorder", Map.of("pair", "XBTZAR"));

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();

        assertFalse(request.headers().firstValue(properties.getNonceHeader()).isPresent());
        assertFalse(request.headers().firstValue(properties.getSignatureHeader()).isPresent());
    }

    private LunoProperties baseProperties() {
        LunoProperties properties = new LunoProperties();
        properties.setBaseUrl("https://api.luno.com");
        properties.setApiKey("test-key");
        properties.setApiSecret("test-secret");
        properties.setNonceHeader("X-LUNO-NONCE");
        properties.setSignatureHeader("X-LUNO-SIGNATURE");
        return properties;
    }
}


