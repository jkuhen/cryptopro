package com.kuhen.cryptopro.preprocess;

import java.util.List;

public record PreprocessingResult(
        List<PreprocessedCandle> candles,
        SessionType sessionType,
        VolatilityRegime volatilityRegime,
        double dataQualityScore
) {
}

