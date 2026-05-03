package com.kuhen.cryptopro.strategy;

import com.kuhen.cryptopro.config.StrategyProperties;
import com.kuhen.cryptopro.preprocess.PreprocessedCandle;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PriceActionPatternDetector {

    private final StrategyProperties strategyProperties;

    public PriceActionPatternDetector(StrategyProperties strategyProperties) {
        this.strategyProperties = strategyProperties;
    }

    public PatternSet detect(List<PreprocessedCandle> candles) {
        if (candles == null || candles.size() < 5) {
            return new PatternSet(
                    PriceActionPatternSignal.none("Insufficient candles for BOS"),
                    PriceActionPatternSignal.none("Insufficient candles for liquidity sweep"),
                    PriceActionPatternSignal.none("Insufficient candles for FVG")
            );
        }

        return new PatternSet(
                detectBreakOfStructure(candles),
                detectLiquiditySweep(candles),
                detectFairValueGap(candles)
        );
    }

    private PriceActionPatternSignal detectBreakOfStructure(List<PreprocessedCandle> candles) {
        StrategyProperties.Smc properties = strategyProperties.getSmc();
        int start = Math.max(0, candles.size() - properties.getLookbackCandles());
        int end = candles.size() - 1;
        if (end - start < 3) {
            return PriceActionPatternSignal.none("Not enough structure points for BOS");
        }

        double swingHigh = latestSwingHigh(candles, start, end);
        double swingLow = latestSwingLow(candles, start, end);
        PreprocessedCandle last = candles.get(end);
        double close = last.cleanClose();
        double closeBuffer = Math.max(close * properties.getBosCloseBufferPercent(), 1e-8);
        double averageRange = averageRange(candles, start, end);

        boolean bullishBos = close > swingHigh + closeBuffer;
        boolean bearishBos = close < swingLow - closeBuffer;

        if (!bullishBos && !bearishBos) {
            return PriceActionPatternSignal.none("No BOS detected");
        }

        if (bullishBos && (!bearishBos || (close - swingHigh) >= (swingLow - close))) {
            double breakoutDistance = close - swingHigh;
            double strength = normalizeStrength(breakoutDistance, averageRange);
            return new PriceActionPatternSignal(
                    true,
                    SignalDirection.LONG,
                    strength,
                    swingHigh,
                    close,
                    "Bullish BOS above swing high"
            );
        }

        double breakoutDistance = swingLow - close;
        double strength = normalizeStrength(breakoutDistance, averageRange);
        return new PriceActionPatternSignal(
                true,
                SignalDirection.SHORT,
                strength,
                swingLow,
                close,
                "Bearish BOS below swing low"
        );
    }

    private PriceActionPatternSignal detectLiquiditySweep(List<PreprocessedCandle> candles) {
        StrategyProperties.Smc properties = strategyProperties.getSmc();
        int start = Math.max(0, candles.size() - properties.getLookbackCandles());
        int end = candles.size() - 1;
        PreprocessedCandle last = candles.get(end);
        double swingHigh = latestSwingHigh(candles, start, end);
        double swingLow = latestSwingLow(candles, start, end);
        double close = last.cleanClose();
        double high = last.raw().high();
        double low = last.raw().low();
        double range = Math.max(high - low, 1e-8);
        double sweepBuffer = Math.max(close * properties.getSweepBufferPercent(), 1e-8);

        boolean bearishSweep = high > swingHigh + sweepBuffer && close < swingHigh;
        boolean bullishSweep = low < swingLow - sweepBuffer && close > swingLow;

        if (!bearishSweep && !bullishSweep) {
            return PriceActionPatternSignal.none("No liquidity sweep detected");
        }

        if (bullishSweep && (!bearishSweep || (close - swingLow) >= (swingHigh - close))) {
            double reclaimDistance = close - swingLow;
            double wickDepth = swingLow - low;
            double strength = normalizeStrength(reclaimDistance + wickDepth, range * 2.0);
            return new PriceActionPatternSignal(
                    true,
                    SignalDirection.LONG,
                    strength,
                    swingLow,
                    close,
                    "Bullish liquidity sweep of prior lows"
            );
        }

        double rejectionDistance = swingHigh - close;
        double wickDepth = high - swingHigh;
        double strength = normalizeStrength(rejectionDistance + wickDepth, range * 2.0);
        return new PriceActionPatternSignal(
                true,
                SignalDirection.SHORT,
                strength,
                swingHigh,
                close,
                "Bearish liquidity sweep of prior highs"
        );
    }

    private PriceActionPatternSignal detectFairValueGap(List<PreprocessedCandle> candles) {
        StrategyProperties.Smc properties = strategyProperties.getSmc();
        int start = Math.max(0, candles.size() - properties.getLookbackCandles());
        double minGapPercent = properties.getMinFairValueGapPercent();

        for (int i = candles.size() - 1; i >= Math.max(start + 2, 2); i--) {
            PreprocessedCandle first = candles.get(i - 2);
            PreprocessedCandle middle = candles.get(i - 1);
            PreprocessedCandle third = candles.get(i);

            double bullishGap = third.raw().low() - first.raw().high();
            if (bullishGap > 0.0) {
                double gapPercent = bullishGap / Math.max(third.cleanClose(), 1e-8);
                if (gapPercent >= minGapPercent && middle.cleanClose() > first.cleanClose()) {
                    return new PriceActionPatternSignal(
                            true,
                            SignalDirection.LONG,
                            normalizeStrength(gapPercent, minGapPercent * 3.0),
                            first.raw().high(),
                            third.raw().low(),
                            "Bullish FVG imbalance detected"
                    );
                }
            }

            double bearishGap = first.raw().low() - third.raw().high();
            if (bearishGap > 0.0) {
                double gapPercent = bearishGap / Math.max(third.cleanClose(), 1e-8);
                if (gapPercent >= minGapPercent && middle.cleanClose() < first.cleanClose()) {
                    return new PriceActionPatternSignal(
                            true,
                            SignalDirection.SHORT,
                            normalizeStrength(gapPercent, minGapPercent * 3.0),
                            first.raw().low(),
                            third.raw().high(),
                            "Bearish FVG imbalance detected"
                    );
                }
            }
        }

        return PriceActionPatternSignal.none("No FVG detected");
    }

    private double latestSwingHigh(List<PreprocessedCandle> candles, int start, int endExclusiveLast) {
        for (int i = endExclusiveLast - 1; i >= start + 1; i--) {
            double current = candles.get(i).raw().high();
            if (current > candles.get(i - 1).raw().high() && current >= candles.get(i + 1).raw().high()) {
                return current;
            }
        }
        return candles.subList(start, endExclusiveLast).stream()
                .mapToDouble(candle -> candle.raw().high())
                .max()
                .orElse(candles.get(endExclusiveLast).raw().high());
    }

    private double latestSwingLow(List<PreprocessedCandle> candles, int start, int endExclusiveLast) {
        for (int i = endExclusiveLast - 1; i >= start + 1; i--) {
            double current = candles.get(i).raw().low();
            if (current < candles.get(i - 1).raw().low() && current <= candles.get(i + 1).raw().low()) {
                return current;
            }
        }
        return candles.subList(start, endExclusiveLast).stream()
                .mapToDouble(candle -> candle.raw().low())
                .min()
                .orElse(candles.get(endExclusiveLast).raw().low());
    }

    private double averageRange(List<PreprocessedCandle> candles, int start, int endExclusive) {
        return candles.subList(start, endExclusive).stream()
                .mapToDouble(candle -> Math.max(candle.raw().high() - candle.raw().low(), 1e-8))
                .average()
                .orElse(1.0);
    }

    private double normalizeStrength(double numerator, double denominator) {
        if (denominator <= 0.0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, numerator / denominator));
    }

    public record PatternSet(
            PriceActionPatternSignal bos,
            PriceActionPatternSignal liquiditySweep,
            PriceActionPatternSignal fairValueGap
    ) {
    }
}

