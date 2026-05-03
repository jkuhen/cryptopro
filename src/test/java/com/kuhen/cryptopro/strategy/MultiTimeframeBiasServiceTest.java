package com.kuhen.cryptopro.strategy;

import com.kuhen.cryptopro.config.StrategyProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiTimeframeBiasServiceTest {

    private final MultiTimeframeBiasService service = new MultiTimeframeBiasService(new StrategyProperties());

    @Test
    void allowsControlledDisagreementWhenHigherTimeframesLead() {
        TrendScore h1 = new TrendScore(SignalDirection.LONG, 0.8);
        TrendScore m15 = new TrendScore(SignalDirection.LONG, 0.5);
        TrendScore m5 = new TrendScore(SignalDirection.SHORT, -0.2);

        WeightedBiasResult result = service.calculate(h1, m15, m5);

        assertTrue(result.allowEntry());
        assertTrue(result.score() > 0.0);
        assertTrue(result.disagreementIndex() > 0.0);
    }
}

