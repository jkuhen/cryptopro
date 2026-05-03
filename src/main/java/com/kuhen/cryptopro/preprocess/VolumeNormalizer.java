package com.kuhen.cryptopro.preprocess;

import com.kuhen.cryptopro.data.model.Candle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class VolumeNormalizer {

    public List<VolumePoint> normalize(List<Candle> candles) {
        if (candles.isEmpty()) {
            return List.of();
        }

        double[] logVolumes = candles.stream().mapToDouble(c -> Math.log(Math.max(c.volume(), 1.0))).toArray();
        double mean = 0.0;
        for (double value : logVolumes) {
            mean += value;
        }
        mean /= logVolumes.length;

        double variance = 0.0;
        for (double value : logVolumes) {
            variance += Math.pow(value - mean, 2.0);
        }
        variance /= logVolumes.length;
        double stdDev = Math.sqrt(variance);

        List<Double> sortedVolumes = candles.stream().map(Candle::volume).sorted(Comparator.naturalOrder()).toList();
        double medianVolume = sortedVolumes.get(sortedVolumes.size() / 2);

        List<VolumePoint> normalized = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            double zScore = stdDev == 0.0 ? 0.0 : (logVolumes[i] - mean) / stdDev;
            double relativeVolume = medianVolume == 0.0 ? 1.0 : candle.volume() / medianVolume;
            normalized.add(new VolumePoint(zScore, relativeVolume));
        }
        return normalized;
    }

    public record VolumePoint(double zScore, double relativeVolume) {
    }
}

