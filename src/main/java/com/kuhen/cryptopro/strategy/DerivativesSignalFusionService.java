package com.kuhen.cryptopro.strategy;

import com.kuhen.cryptopro.config.StrategyProperties;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.FundingRate;
import com.kuhen.cryptopro.data.model.LiquidationEvent;
import com.kuhen.cryptopro.data.model.LiquidationSide;
import com.kuhen.cryptopro.data.model.OpenInterestSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DerivativesSignalFusionService {

    private final StrategyProperties strategyProperties;

    /** Default constructor for framework instantiation. */
    public DerivativesSignalFusionService() {
        this.strategyProperties = null;
    }

    @Autowired
    public DerivativesSignalFusionService(StrategyProperties strategyProperties) {
        this.strategyProperties = strategyProperties;
    }

    public DerivativesSignal evaluate(
            List<Candle> candles,
            List<OpenInterestSnapshot> openInterest,
            FundingRate fundingRate,
            List<LiquidationEvent> liquidations
    ) {
        if (candles.size() < 2 || openInterest.size() < 2) {
            return new DerivativesSignal(SignalDirection.NEUTRAL, 0.0, 0.0, fundingRate.rate(), 0.0, "Insufficient derivatives context");
        }

        double firstPrice = candles.get(0).close();
        double lastPrice = candles.get(candles.size() - 1).close();
        double priceDelta = firstPrice == 0.0 ? 0.0 : (lastPrice - firstPrice) / firstPrice;

        double firstOi = openInterest.get(0).openInterestUsd();
        double lastOi = openInterest.get(openInterest.size() - 1).openInterestUsd();
        double oiDelta = firstOi == 0.0 ? 0.0 : (lastOi - firstOi) / firstOi;

        double baseScore;
        SignalDirection baseDirection;
        if (oiDelta > 0 && priceDelta > 0) {
            baseScore = strategyProperties.getThresholds().getOiPriceUpScore();
            baseDirection = SignalDirection.LONG;
        } else if (oiDelta > 0 && priceDelta < 0) {
            baseScore = strategyProperties.getThresholds().getOiPriceDownScore();
            baseDirection = SignalDirection.SHORT;
        } else {
            baseScore = 0.0;
            baseDirection = SignalDirection.NEUTRAL;
        }

        double fundingAdjustment = fundingAdjustment(fundingRate.rate());
        double liquidationAdjustment = liquidationAdjustment(liquidations);

        double score = Math.max(-1.0, Math.min(1.0, baseScore + fundingAdjustment + liquidationAdjustment));
        double directionCutoff = strategyProperties.getThresholds().getDerivativesDirectionCutoff();
        SignalDirection direction = score > directionCutoff ? SignalDirection.LONG : score < -directionCutoff ? SignalDirection.SHORT : SignalDirection.NEUTRAL;

        String notes = "OI delta=" + round(oiDelta) + ", price delta=" + round(priceDelta)
                + ", funding=" + round(fundingRate.rate()) + ", liquidationAdj=" + round(liquidationAdjustment);

        return new DerivativesSignal(direction, score, oiDelta, fundingRate.rate(), priceDelta, notes);
    }

    private double fundingAdjustment(double fundingRate) {
        double extreme = strategyProperties.getThresholds().getFundingExtremeRate();
        if (fundingRate > extreme) {
            return -0.2;
        }
        if (fundingRate < -extreme) {
            return 0.2;
        }
        return 0.0;
    }

    private double liquidationAdjustment(List<LiquidationEvent> liquidations) {
        double longLiq = liquidations.stream()
                .filter(l -> l.side() == LiquidationSide.LONG)
                .mapToDouble(LiquidationEvent::sizeUsd)
                .sum();
        double shortLiq = liquidations.stream()
                .filter(l -> l.side() == LiquidationSide.SHORT)
                .mapToDouble(LiquidationEvent::sizeUsd)
                .sum();

        double total = longLiq + shortLiq;
        if (total == 0.0) {
            return 0.0;
        }

        double imbalance = (shortLiq - longLiq) / total;
        double scale = strategyProperties.getThresholds().getDerivativesImbalanceScale();
        double cap = strategyProperties.getThresholds().getDerivativesImbalanceCap();
        return Math.max(-cap, Math.min(cap, imbalance * scale));
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}

