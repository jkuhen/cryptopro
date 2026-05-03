package com.kuhen.cryptopro.preprocess;

import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.config.PreprocessingProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SpikeCleaner {

    private final PreprocessingProperties preprocessingProperties;

    public SpikeCleaner(PreprocessingProperties preprocessingProperties) {
        this.preprocessingProperties = preprocessingProperties;
    }

    public List<Candle> clean(List<Candle> candles) {
        if (candles.isEmpty()) {
            return List.of();
        }

        double mean = candles.stream().mapToDouble(Candle::close).average().orElse(0.0);
        double variance = candles.stream()
                .mapToDouble(c -> Math.pow(c.close() - mean, 2.0))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);
        double multiplier = preprocessingProperties.getSpikeStddevMultiplier();
        double upper = mean + (multiplier * stdDev);
        double lower = mean - (multiplier * stdDev);

        List<Candle> cleaned = new ArrayList<>(candles.size());
        for (Candle candle : candles) {
            double cleanClose = Math.min(upper, Math.max(lower, candle.close()));
            cleaned.add(new Candle(
                    candle.symbol(),
                    candle.timeframe(),
                    candle.openTime(),
                    candle.open(),
                    candle.high(),
                    candle.low(),
                    cleanClose,
                    candle.volume()
            ));
        }
        return cleaned;
    }
}

