package com.kuhen.cryptopro.ai;

import com.kuhen.cryptopro.data.model.Candle;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RegimeFeatureEngineeringService {

    public RegimeClassificationFeatures build(
            List<Candle> h1Candles,
            List<Candle> m15Candles,
            List<Candle> entryCandles,
            double volumeSpike,
            double openInterestChange,
            double fundingRate
    ) {
        double h1TrendSlope = normalizedSlope(lastCloses(h1Candles, 24));
        double m15TrendSlope = normalizedSlope(lastCloses(m15Candles, 32));

        List<Double> entryCloses = lastCloses(entryCandles, 80);
        double realizedVolatility = realizedVolatility(entryCloses);
        double atrPercent = atrPercent(lastCandles(entryCandles, 20));
        double rangeCompression = rangeCompression(lastCandles(entryCandles, 48));

        return new RegimeClassificationFeatures(
                h1TrendSlope,
                m15TrendSlope,
                realizedVolatility,
                atrPercent,
                rangeCompression,
                volumeSpike,
                openInterestChange,
                fundingRate
        );
    }

    private List<Double> lastCloses(List<Candle> candles, int limit) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, candles.size() - limit);
        List<Double> closes = new ArrayList<>(candles.size() - from);
        for (int i = from; i < candles.size(); i++) {
            closes.add(candles.get(i).close());
        }
        return closes;
    }

    private List<Candle> lastCandles(List<Candle> candles, int limit) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, candles.size() - limit);
        return candles.subList(from, candles.size());
    }

    private double normalizedSlope(List<Double> closes) {
        if (closes.size() < 2) {
            return 0.0;
        }
        double first = closes.get(0);
        double last = closes.get(closes.size() - 1);
        if (first == 0.0) {
            return 0.0;
        }
        return clamp((last - first) / first, -1.0, 1.0);
    }

    private double realizedVolatility(List<Double> closes) {
        if (closes.size() < 3) {
            return 0.0;
        }
        double mean = 0.0;
        List<Double> returns = new ArrayList<>(closes.size() - 1);
        for (int i = 1; i < closes.size(); i++) {
            double prev = closes.get(i - 1);
            double curr = closes.get(i);
            if (prev <= 0.0) {
                continue;
            }
            double r = Math.log(curr / prev);
            returns.add(r);
            mean += r;
        }
        if (returns.size() < 2) {
            return 0.0;
        }
        mean /= returns.size();

        double variance = 0.0;
        for (double r : returns) {
            double d = r - mean;
            variance += d * d;
        }
        variance /= (returns.size() - 1);
        return Math.sqrt(Math.max(variance, 0.0));
    }

    private double atrPercent(List<Candle> candles) {
        if (candles.size() < 2) {
            return 0.0;
        }
        double trSum = 0.0;
        int count = 0;
        for (int i = 1; i < candles.size(); i++) {
            Candle prev = candles.get(i - 1);
            Candle curr = candles.get(i);
            double highLow = curr.high() - curr.low();
            double highClose = Math.abs(curr.high() - prev.close());
            double lowClose = Math.abs(curr.low() - prev.close());
            trSum += Math.max(highLow, Math.max(highClose, lowClose));
            count++;
        }
        if (count == 0) {
            return 0.0;
        }
        double atr = trSum / count;
        double refClose = candles.get(candles.size() - 1).close();
        if (refClose <= 0.0) {
            return 0.0;
        }
        return atr / refClose;
    }

    private double rangeCompression(List<Candle> candles) {
        if (candles.size() < 5) {
            return 0.0;
        }
        double highest = candles.stream().mapToDouble(Candle::high).max().orElse(0.0);
        double lowest = candles.stream().mapToDouble(Candle::low).min().orElse(0.0);
        double totalRange = Math.max(highest - lowest, 1e-8);

        int tail = Math.max(4, candles.size() / 4);
        List<Candle> recent = candles.subList(candles.size() - tail, candles.size());
        double recentHigh = recent.stream().mapToDouble(Candle::high).max().orElse(highest);
        double recentLow = recent.stream().mapToDouble(Candle::low).min().orElse(lowest);
        double recentRange = Math.max(recentHigh - recentLow, 1e-8);

        return clamp(1.0 - (recentRange / totalRange), 0.0, 1.0);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

