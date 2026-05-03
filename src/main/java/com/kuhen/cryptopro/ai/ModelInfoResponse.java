package com.kuhen.cryptopro.ai;

import java.util.Map;

public record ModelInfoResponse(
        String provider,
        String modelName,
        String modelVersion,
        String artifactPath,
        boolean loaded,
        Map<String, Double> metadataMetrics,
        String notes
) {
}

