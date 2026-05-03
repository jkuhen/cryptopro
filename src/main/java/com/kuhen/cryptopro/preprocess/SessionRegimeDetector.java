package com.kuhen.cryptopro.preprocess;

import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.config.PreprocessingProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class SessionRegimeDetector {

    private final PreprocessingProperties preprocessingProperties;

    public SessionRegimeDetector(PreprocessingProperties preprocessingProperties) {
        this.preprocessingProperties = preprocessingProperties;
    }

    public SessionType detectSessionType(Instant timestamp) {
        int hour = timestamp.atZone(ZoneOffset.UTC).getHour();
        if (hour >= 13 && hour < 16) {
            return SessionType.OVERLAP;
        }
        if (hour >= 0 && hour < 7) {
            return SessionType.ASIA;
        }
        if (hour >= 7 && hour < 13) {
            return SessionType.EUROPE;
        }
        return SessionType.US;
    }

    public VolatilityRegime detectVolatilityRegime(List<Candle> candles) {
        if (candles.size() < 2) {
            return VolatilityRegime.NORMAL;
        }

        double sum = 0.0;
        int count = 0;
        for (int i = 1; i < candles.size(); i++) {
            double previous = candles.get(i - 1).close();
            double current = candles.get(i).close();
            if (previous <= 0.0) {
                continue;
            }
            sum += Math.abs((current - previous) / previous);
            count++;
        }

        if (count == 0) {
            return VolatilityRegime.NORMAL;
        }

        double avgMove = sum / count;
        if (avgMove < preprocessingProperties.getVolatilityLowThreshold()) {
            return VolatilityRegime.LOW;
        }
        if (avgMove > preprocessingProperties.getVolatilityHighThreshold()) {
            return VolatilityRegime.HIGH;
        }
        return VolatilityRegime.NORMAL;
    }
}

