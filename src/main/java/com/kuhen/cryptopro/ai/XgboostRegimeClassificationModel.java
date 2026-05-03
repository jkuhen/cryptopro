package com.kuhen.cryptopro.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuhen.cryptopro.config.AiProperties;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class XgboostRegimeClassificationModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(XgboostRegimeClassificationModel.class);

    private static final List<String> DEFAULT_FEATURE_ORDER = List.of(
            "h1TrendSlope",
            "m15TrendSlope",
            "realizedVolatility",
            "atrPercent",
            "rangeCompression",
            "volumeSpike",
            "openInterestChange",
            "fundingRate"
    );

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    private volatile Booster booster;
    private volatile String loadedVersion;
    private volatile List<String> featureOrder = DEFAULT_FEATURE_ORDER;
    private volatile Path resolvedArtifactPath;
    private volatile Map<String, Double> metadataMetrics = Map.of();

    public XgboostRegimeClassificationModel(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public RegimeDetectionResult classify(RegimeClassificationFeatures features) {
        try {
            ensureLoaded();
            if (booster == null) {
                return fallback(features, "Regime artifact unavailable; using historical-feature heuristic");
            }

            float[] vector = buildFeatureVector(features, featureOrder);
            DMatrix row = new DMatrix(vector, 1, vector.length, Float.NaN);
            float[][] prediction = booster.predict(row);
            if (prediction.length == 0 || prediction[0].length == 0) {
                return fallback(features, "Regime model returned empty output; fallback heuristic");
            }

            if (prediction[0].length == 1) {
                return fallback(features, "Binary regime output detected; fallback heuristic");
            }

            int winningClass = argmax(prediction[0]);
            double confidence = confidence(prediction[0]);
            MarketRegime regime = classToRegime(winningClass);
            return new RegimeDetectionResult(
                    regime,
                    confidence,
                    "xgboost-regime artifact, class=" + winningClass + ", version=" + resolvedVersion()
            );
        } catch (Exception ex) {
            return fallback(features, "Regime scoring failed; fallback heuristic");
        }
    }

    public ModelInfoResponse modelInfo() {
        ensureLoaded();
        String artifactPath = resolvedArtifactPath == null ? "N/A" : resolvedArtifactPath.toAbsolutePath().toString();
        return new ModelInfoResponse(
                "xgboost-regime-artifact",
                aiProperties.getModelName(),
                resolvedVersion(),
                artifactPath,
                booster != null,
                metadataMetrics,
                booster == null ? "Fallback-only regime classification" : "Regime classifier ready"
        );
    }

    private synchronized void ensureLoaded() {
        String targetVersion = aiProperties.getRegimeModel().getVersion();
        if (booster != null && targetVersion.equals(loadedVersion)) {
            return;
        }

        Path versionPath = Paths.get(aiProperties.getRegimeModel().getArtifactRoot(), targetVersion);
        Path modelPath = versionPath.resolve(aiProperties.getRegimeModel().getBinaryFile());
        Path metadataPath = versionPath.resolve(aiProperties.getRegimeModel().getMetadataFile());
        resolvedArtifactPath = modelPath;
        metadataMetrics = loadMetrics(metadataPath);

        if (!Files.exists(modelPath)) {
            if (aiProperties.getRegimeModel().isFailOnMissingArtifact()) {
                throw new IllegalStateException("Regime model file not found: " + modelPath.toAbsolutePath());
            }
            LOGGER.warn("Regime model file not found at {}. Falling back to heuristic classification.", modelPath.toAbsolutePath());
            booster = null;
            loadedVersion = targetVersion;
            featureOrder = DEFAULT_FEATURE_ORDER;
            return;
        }

        try {
            booster = XGBoost.loadModel(modelPath.toString());
            loadedVersion = targetVersion;
            featureOrder = loadFeatureOrder(metadataPath);
            LOGGER.info("Loaded XGBoost regime classifier version {} from {}", loadedVersion, modelPath.toAbsolutePath());
        } catch (XGBoostError ex) {
            if (aiProperties.getRegimeModel().isFailOnMissingArtifact()) {
                throw new IllegalStateException("Failed to load XGBoost regime model", ex);
            }
            LOGGER.warn("Failed to load regime model artifact. Using heuristic fallback.", ex);
            booster = null;
            loadedVersion = targetVersion;
            featureOrder = DEFAULT_FEATURE_ORDER;
        }
    }

    private RegimeDetectionResult fallback(RegimeClassificationFeatures features, String reason) {
        AiProperties.Thresholds thresholds = aiProperties.getThresholds();
        if (features.realizedVolatility() >= thresholds.getHighVolatilityStdMin()
                || features.atrPercent() >= thresholds.getHighVolatilityAtrPercentMin()
                || features.volumeSpike() >= thresholds.getManipulationVolumeSpikeMin()) {
            double confidence = clamp(Math.max(
                    features.realizedVolatility() / Math.max(thresholds.getHighVolatilityStdMin(), 0.0001),
                    features.atrPercent() / Math.max(thresholds.getHighVolatilityAtrPercentMin(), 0.0001)
            ) / 2.0, 0.0, 1.0);
            return new RegimeDetectionResult(MarketRegime.HIGH_VOLATILITY, confidence, reason + " => high volatility");
        }

        double trendComposite = Math.abs((features.h1TrendSlope() * 0.7) + (features.m15TrendSlope() * 0.3));
        boolean trending = trendComposite >= thresholds.getTrendingAlignmentMin()
                && features.rangeCompression() >= thresholds.getTrendingRangeCompressionMin();
        if (trending) {
            return new RegimeDetectionResult(
                    MarketRegime.TRENDING,
                    clamp(trendComposite, 0.0, 1.0),
                    reason + " => trending"
            );
        }

        return new RegimeDetectionResult(
                MarketRegime.RANGING,
                clamp(1.0 - trendComposite, 0.0, 1.0),
                reason + " => ranging"
        );
    }

    private float[] buildFeatureVector(RegimeClassificationFeatures features, List<String> order) {
        float[] vector = new float[order.size()];
        for (int i = 0; i < order.size(); i++) {
            String name = order.get(i);
            vector[i] = switch (name) {
                case "h1TrendSlope" -> (float) features.h1TrendSlope();
                case "m15TrendSlope" -> (float) features.m15TrendSlope();
                case "realizedVolatility" -> (float) features.realizedVolatility();
                case "atrPercent" -> (float) features.atrPercent();
                case "rangeCompression" -> (float) features.rangeCompression();
                case "volumeSpike" -> (float) features.volumeSpike();
                case "openInterestChange" -> (float) features.openInterestChange();
                case "fundingRate" -> (float) features.fundingRate();
                default -> 0.0f;
            };
        }
        return vector;
    }

    private List<String> loadFeatureOrder(Path metadataPath) {
        if (!Files.exists(metadataPath)) {
            return DEFAULT_FEATURE_ORDER;
        }
        try (InputStream inputStream = Files.newInputStream(metadataPath)) {
            JsonNode root = objectMapper.readTree(inputStream);
            JsonNode featuresNode = root.path("featureOrder");
            if (featuresNode.isArray() && !featuresNode.isEmpty()) {
                return objectMapper.convertValue(featuresNode, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed reading regime metadata from {}. Using default feature order.", metadataPath.toAbsolutePath());
        }
        return DEFAULT_FEATURE_ORDER;
    }

    private Map<String, Double> loadMetrics(Path metadataPath) {
        if (!Files.exists(metadataPath)) {
            return Map.of();
        }
        try (InputStream inputStream = Files.newInputStream(metadataPath)) {
            JsonNode root = objectMapper.readTree(inputStream);
            JsonNode metricsNode = root.path("metrics");
            if (!metricsNode.isObject()) {
                return Map.of();
            }
            Map<String, Double> result = new HashMap<>();
            metricsNode.fields().forEachRemaining(entry -> {
                if (entry.getValue().isNumber()) {
                    result.put(entry.getKey(), entry.getValue().asDouble());
                }
            });
            return result;
        } catch (IOException ex) {
            LOGGER.warn("Failed reading regime metrics from {}", metadataPath.toAbsolutePath());
            return Map.of();
        }
    }

    private MarketRegime classToRegime(int cls) {
        return switch (cls) {
            case 0 -> MarketRegime.TRENDING;
            case 1 -> MarketRegime.RANGING;
            case 2 -> MarketRegime.HIGH_VOLATILITY;
            default -> MarketRegime.RANGING;
        };
    }

    private int argmax(float[] values) {
        int idx = 0;
        float best = values[0];
        for (int i = 1; i < values.length; i++) {
            if (values[i] > best) {
                best = values[i];
                idx = i;
            }
        }
        return idx;
    }

    private double confidence(float[] values) {
        if (values.length < 2) {
            return clamp(values[0], 0.0, 1.0);
        }
        float best = -Float.MAX_VALUE;
        float second = -Float.MAX_VALUE;
        for (float value : values) {
            if (value > best) {
                second = best;
                best = value;
            } else if (value > second) {
                second = value;
            }
        }
        return clamp(best - second, 0.0, 1.0);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String resolvedVersion() {
        return loadedVersion == null ? aiProperties.getRegimeModel().getVersion() : loadedVersion;
    }
}

