package com.kuhen.cryptopro.preprocess;

import com.kuhen.cryptopro.data.model.Candle;

public record PreprocessedCandle(
        Candle raw,
        double cleanClose,
        double volumeZScore,
        double relativeVolume
) {
}

