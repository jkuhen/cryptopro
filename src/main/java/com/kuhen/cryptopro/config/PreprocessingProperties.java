package com.kuhen.cryptopro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cryptopro.preprocessing")
public class PreprocessingProperties {

    private double spikeStddevMultiplier = 3.0;
    private double volatilityLowThreshold = 0.002;
    private double volatilityHighThreshold = 0.008;

    public double getSpikeStddevMultiplier() {
        return spikeStddevMultiplier;
    }

    public void setSpikeStddevMultiplier(double spikeStddevMultiplier) {
        this.spikeStddevMultiplier = spikeStddevMultiplier;
    }

    public double getVolatilityLowThreshold() {
        return volatilityLowThreshold;
    }

    public void setVolatilityLowThreshold(double volatilityLowThreshold) {
        this.volatilityLowThreshold = volatilityLowThreshold;
    }

    public double getVolatilityHighThreshold() {
        return volatilityHighThreshold;
    }

    public void setVolatilityHighThreshold(double volatilityHighThreshold) {
        this.volatilityHighThreshold = volatilityHighThreshold;
    }
}

