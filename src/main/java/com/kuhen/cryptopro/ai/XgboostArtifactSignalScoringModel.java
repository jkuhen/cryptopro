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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "cryptopro.ai.model", name = "provider", havingValue = "xgboost-artifact", matchIfMissing = true)
public class XgboostArtifactSignalScoringModel implements SignalScoringModel, ModelInfoProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(XgboostArtifactSignalScoringModel.class);

    private static final List<String> DEFAULT_FEATURE_ORDER = List.of(
            "trendScore",
            "rsi",
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

    public XgboostArtifactSignalScoringModel(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public SignalScoringResult score(SignalScoringFeatures features) {
        try {
            ensureLoaded();
            if (booster == null) {
                return fallbackResult(features, "Model artifact unavailable; using deterministic fallback");
            }

            float[] vector = buildFeatureVector(features, featureOrder);
            DMatrix row = new DMatrix(vector, 1, vector.length, Float.NaN);
            float[][] output = booster.predict(row);
            double probability = (output.length == 0 || output[0].length == 0) ? 0.5 : clamp(output[0][0], 0.0, 1.0);
            double confidence = Math.min(1.0, Math.abs(probability - 0.5) * 2.0);

            return new SignalScoringResult(
                    probability,
                    confidence,
                    aiProperties.getModelName(),
                    loadedVersion == null ? aiProperties.getModel().getVersion() : loadedVersion,
                    "artifact=xgboost, features=" + Arrays.toString(featureOrder.toArray())
            );
        } catch (Exception ex) {
            if (aiProperties.getModel().isFailOnMissingArtifact()) {
                throw new IllegalStateException("Unable to load or score XGBoost model artifact", ex);
            }
            return fallbackResult(features, "Artifact scoring failed; using deterministic fallback");
        }
    }

    @Override
    public ModelInfoResponse currentModelInfo() {
        ensureLoaded();
        String version = loadedVersion == null ? aiProperties.getModel().getVersion() : loadedVersion;
        String artifactPath = resolvedArtifactPath == null ? "N/A" : resolvedArtifactPath.toAbsolutePath().toString();
        String notes = booster == null
                ? "Artifact scorer loaded without model binary; fallback scoring in use"
                : "Artifact scorer ready";

        return new ModelInfoResponse(
                "xgboost-artifact",
                aiProperties.getModelName(),
                version,
                artifactPath,
                booster != null,
                metadataMetrics,
                notes
        );
    }

    private synchronized void ensureLoaded() {
        String targetVersion = aiProperties.getModel().getVersion();
        if (booster != null && targetVersion.equals(loadedVersion)) {
            return;
        }

        Path versionPath = Paths.get(aiProperties.getModel().getArtifactRoot(), targetVersion);
        Path modelPath = versionPath.resolve(aiProperties.getModel().getBinaryFile());
        Path metadataPath = versionPath.resolve(aiProperties.getModel().getMetadataFile());
        resolvedArtifactPath = modelPath;
        metadataMetrics = loadMetrics(metadataPath);

        if (!Files.exists(modelPath)) {
            if (aiProperties.getModel().isFailOnMissingArtifact()) {
                throw new IllegalStateException("XGBoost model file not found: " + modelPath.toAbsolutePath());
            }
            LOGGER.warn("XGBoost model file not found at {}. Falling back to deterministic scoring.", modelPath.toAbsolutePath());
            booster = null;
            loadedVersion = targetVersion;
            featureOrder = DEFAULT_FEATURE_ORDER;
            return;
        }

        try {
            booster = XGBoost.loadModel(modelPath.toString());
            loadedVersion = targetVersion;
            featureOrder = loadFeatureOrder(metadataPath);
            metadataMetrics = loadMetrics(metadataPath);
            LOGGER.info("Loaded XGBoost artifact model version {} from {}", loadedVersion, modelPath.toAbsolutePath());
        } catch (XGBoostError ex) {
            if (aiProperties.getModel().isFailOnMissingArtifact()) {
                throw new IllegalStateException("Failed to load XGBoost model artifact", ex);
            }
            LOGGER.warn("Failed to load XGBoost artifact. Falling back to deterministic scoring.", ex);
            booster = null;
            loadedVersion = targetVersion;
            featureOrder = DEFAULT_FEATURE_ORDER;
        }
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
                JsonNode valueNode = entry.getValue();
                if (valueNode.isNumber()) {
                    result.put(entry.getKey(), valueNode.asDouble());
                }
            });
            return result;
        } catch (IOException ex) {
            LOGGER.warn("Failed reading metrics from metadata at {}", metadataPath.toAbsolutePath());
            return Map.of();
        }
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
            LOGGER.warn("Failed reading model metadata from {}. Using default feature order.", metadataPath.toAbsolutePath());
        }
        return DEFAULT_FEATURE_ORDER;
    }

    private float[] buildFeatureVector(SignalScoringFeatures features, List<String> order) {
        float[] vector = new float[order.size()];
        for (int i = 0; i < order.size(); i++) {
            String name = order.get(i);
            vector[i] = switch (name) {
                case "trendScore" -> (float) features.trendScore();
                case "trendAlignmentScore" -> (float) features.trendScore();
                case "rsi" -> (float) features.rsi();
                case "volumeSpike" -> (float) features.volumeSpike();
                case "openInterestChange" -> (float) features.openInterestChange();
                case "oiChangePercent" -> (float) features.openInterestChange();
                case "fundingRate" -> (float) features.fundingRate();
                case "liquiditySweepDetected", "volatilityRegimeEncoded" -> 0.0f;
                default -> 0.0f;
            };
        }
        return vector;
    }

    private SignalScoringResult fallbackResult(SignalScoringFeatures features, String reason) {
        double score = (0.45 * features.trendScore())
                + (0.20 * normalizeRsi(features.rsi()))
                + (0.20 * Math.min(features.volumeSpike(), 3.0))
                + (0.10 * (features.openInterestChange() / 3.0))
                - (0.05 * Math.abs(features.fundingRate()) * 10.0);

        double probability = 1.0 / (1.0 + Math.exp(-score));
        double confidence = Math.min(1.0, Math.abs(probability - 0.5) * 2.0);
        return new SignalScoringResult(probability, confidence, aiProperties.getModelName(), aiProperties.getModel().getVersion(), reason);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double normalizeRsi(double rsi) {
        return clamp((rsi - 50.0) / 50.0, -1.0, 1.0);
    }
}




