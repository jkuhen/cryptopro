package com.kuhen.cryptopro.data.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Minimal Binance kline event DTO for WebSocket deserialisation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceKlineEventDto(
        @JsonProperty("e") String eventType,
        @JsonProperty("E") long eventTimeMs,
        @JsonProperty("s") String symbol,
        @JsonProperty("k") BinanceKlinePayloadDto kline
) {

    public boolean isKlineEvent() {
        return "kline".equalsIgnoreCase(eventType) && kline != null;
    }
}

