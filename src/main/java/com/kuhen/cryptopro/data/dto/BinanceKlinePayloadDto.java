package com.kuhen.cryptopro.data.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.Timeframe;

import java.time.Instant;

/**
 * Minimal Binance kline payload mapped from the {@code k} field of a kline event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceKlinePayloadDto(
        @JsonProperty("t") long openTimeMs,
        @JsonProperty("T") long closeTimeMs,
        @JsonProperty("s") String symbol,
        @JsonProperty("i") String interval,
        @JsonProperty("o") String open,
        @JsonProperty("c") String close,
        @JsonProperty("h") String high,
        @JsonProperty("l") String low,
        @JsonProperty("v") String volume,
        @JsonProperty("x") boolean closed
) {

    public Candle toCandle(Timeframe timeframe) {
        return new Candle(
                symbol,
                timeframe,
                Instant.ofEpochMilli(openTimeMs),
                parse(open),
                parse(high),
                parse(low),
                parse(close),
                parse(volume)
        );
    }

    private static double parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }
}

