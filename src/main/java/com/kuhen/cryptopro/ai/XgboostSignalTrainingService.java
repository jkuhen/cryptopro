package com.kuhen.cryptopro.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuhen.cryptopro.config.AiProperties;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class XgboostSignalTrainingService {

    private static final List<String> FEATURE_ORDER = List.of(
            "trendScore",
            "rsi",
            "volumeSpike",
            "openInterestChange",
            "fundingRate"
    );

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public XgboostSignalTrainingService(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public TrainingArtifact train(List<TrainingRow> rows, String version) {
        if (rows == null || rows.size() < 20) {
            throw new IllegalArgumentException("At least 20 labeled rows are required to train an XGBoost model");
        }

        List<TrainingRow> shuffled = new ArrayList<>(rows);
        java.util.Collections.shuffle(shuffled, new java.util.Random(42L));
        int split = Math.max(1, (int) Math.round(shuffled.size() * 0.8));
        if (split >= shuffled.size()) {
            split = shuffled.size() - 1;
        }

        List<TrainingRow> trainRows = shuffled.subList(0, split);
        List<TrainingRow> validationRows = shuffled.subList(split, shuffled.size());

        try {
            DMatrix trainMatrix = toMatrix(trainRows);
            DMatrix validationMatrix = toMatrix(validationRows);
            Booster booster = XGBoost.train(
                    trainMatrix,
                    trainingParams(),
                    40,
                    Map.of("train", trainMatrix, "validation", validationMatrix),
                    null,
                    null
            );

            float[][] predictions = booster.predict(validationMatrix);
            Map<String, Double> metrics = evaluate(validationRows, predictions);
            String resolvedVersion = (version == null || version.isBlank()) ? aiProperties.getModel().getVersion() : version;
            Path versionDir = Path.of(aiProperties.getModel().getArtifactRoot(), resolvedVersion);
            Files.createDirectories(versionDir);

            Path modelPath = versionDir.resolve(aiProperties.getModel().getBinaryFile());
            Path metadataPath = versionDir.resolve(aiProperties.getModel().getMetadataFile());
            booster.saveModel(modelPath.toString());
            Files.writeString(metadataPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(buildMetadata(resolvedVersion, metrics)));

            return new TrainingArtifact(modelPath, metadataPath, resolvedVersion, trainRows.size(), validationRows.size(), metrics);
        } catch (IOException | XGBoostError ex) {
            throw new IllegalStateException("Failed to train XGBoost signal model", ex);
        }
    }

    public List<TrainingRow> loadCsv(Path datasetPath) {
        try {
            List<String> lines = Files.readAllLines(datasetPath).stream()
                    .filter(line -> !line.isBlank())
                    .toList();
            if (lines.size() < 2) {
                return List.of();
            }

            String[] header = lines.get(0).split(",");
            Map<String, Integer> positions = new LinkedHashMap<>();
            for (int i = 0; i < header.length; i++) {
                positions.put(header[i].trim(), i);
            }

            List<String> required = new ArrayList<>(FEATURE_ORDER);
            required.add("label");
            for (String column : required) {
                if (!positions.containsKey(column)) {
                    throw new IllegalArgumentException("Dataset missing required column: " + column);
                }
            }

            return lines.subList(1, lines.size()).stream()
                    .map(line -> parseRow(line, positions))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed reading training dataset from " + datasetPath, ex);
        }
    }

    private TrainingRow parseRow(String line, Map<String, Integer> positions) {
        String[] values = line.split(",");
        return new TrainingRow(
                Double.parseDouble(values[positions.get("trendScore")].trim()),
                Double.parseDouble(values[positions.get("rsi")].trim()),
                Double.parseDouble(values[positions.get("volumeSpike")].trim()),
                Double.parseDouble(values[positions.get("openInterestChange")].trim()),
                Double.parseDouble(values[positions.get("fundingRate")].trim()),
                Integer.parseInt(values[positions.get("label")].trim())
        );
    }

    private DMatrix toMatrix(List<TrainingRow> rows) throws XGBoostError {
        float[] features = new float[rows.size() * FEATURE_ORDER.size()];
        float[] labels = new float[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            TrainingRow row = rows.get(i);
            int offset = i * FEATURE_ORDER.size();
            features[offset] = (float) row.trendScore();
            features[offset + 1] = (float) row.rsi();
            features[offset + 2] = (float) row.volumeSpike();
            features[offset + 3] = (float) row.openInterestChange();
            features[offset + 4] = (float) row.fundingRate();
            labels[i] = row.label();
        }
        DMatrix matrix = new DMatrix(features, rows.size(), FEATURE_ORDER.size(), Float.NaN);
        matrix.setLabel(labels);
        return matrix;
    }

    private Map<String, Object> buildMetadata(String version, Map<String, Double> metrics) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("modelName", aiProperties.getModelName());
        metadata.put("version", version);
        metadata.put("trainedAtUtc", Instant.now().toString());
        metadata.put("featureOrder", FEATURE_ORDER);
        metadata.put("objective", "binary:logistic");
        metadata.put("metrics", metrics);
        metadata.put("binaryFile", aiProperties.getModel().getBinaryFile());
        return metadata;
    }

    private Map<String, Object> trainingParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("objective", "binary:logistic");
        params.put("eval_metric", "logloss");
        params.put("eta", 0.08);
        params.put("max_depth", 4);
        params.put("subsample", 0.9);
        params.put("colsample_bytree", 0.9);
        params.put("seed", 42);
        return params;
    }

    private Map<String, Double> evaluate(List<TrainingRow> rows, float[][] predictions) {
        double logLoss = 0.0;
        double correct = 0.0;
        for (int i = 0; i < rows.size(); i++) {
            double probability = clamp(predictions[i][0], 1e-6, 1.0 - 1e-6);
            int label = rows.get(i).label();
            logLoss += -(label * Math.log(probability) + (1 - label) * Math.log(1.0 - probability));
            int predicted = probability >= 0.5 ? 1 : 0;
            if (predicted == label) {
                correct++;
            }
        }

        return Map.of(
                "logLoss", logLoss / rows.size(),
                "accuracy", correct / rows.size(),
                "validationRows", (double) rows.size()
        );
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record TrainingRow(
            double trendScore,
            double rsi,
            double volumeSpike,
            double openInterestChange,
            double fundingRate,
            int label
    ) {
    }

    public record TrainingArtifact(
            Path modelPath,
            Path metadataPath,
            String version,
            int trainRows,
            int validationRows,
            Map<String, Double> metrics
    ) {
    }
}

