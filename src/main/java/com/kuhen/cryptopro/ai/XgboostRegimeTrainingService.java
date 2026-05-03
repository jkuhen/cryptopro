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
public class XgboostRegimeTrainingService {

    private static final List<String> FEATURE_ORDER = List.of(
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

    public XgboostRegimeTrainingService(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public TrainingArtifact train(List<TrainingRow> rows, String version) {
        if (rows == null || rows.size() < 30) {
            throw new IllegalArgumentException("At least 30 rows are required to train regime classifier");
        }

        List<TrainingRow> shuffled = new ArrayList<>(rows);
        java.util.Collections.shuffle(shuffled, new java.util.Random(7L));
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
                    params(),
                    60,
                    Map.of("train", trainMatrix, "validation", validationMatrix),
                    null,
                    null
            );

            float[][] predictions = booster.predict(validationMatrix);
            Map<String, Double> metrics = evaluate(validationRows, predictions);
            String resolvedVersion = (version == null || version.isBlank()) ? aiProperties.getRegimeModel().getVersion() : version;
            Path versionDir = Path.of(aiProperties.getRegimeModel().getArtifactRoot(), resolvedVersion);
            Files.createDirectories(versionDir);

            Path modelPath = versionDir.resolve(aiProperties.getRegimeModel().getBinaryFile());
            Path metadataPath = versionDir.resolve(aiProperties.getRegimeModel().getMetadataFile());
            booster.saveModel(modelPath.toString());
            Files.writeString(metadataPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata(resolvedVersion, metrics)));

            return new TrainingArtifact(modelPath, metadataPath, resolvedVersion, trainRows.size(), validationRows.size(), metrics);
        } catch (IOException | XGBoostError ex) {
            throw new IllegalStateException("Failed to train regime classifier", ex);
        }
    }

    public List<TrainingRow> loadCsv(Path datasetPath) {
        try {
            List<String> lines = Files.readAllLines(datasetPath).stream().filter(line -> !line.isBlank()).toList();
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

            return lines.subList(1, lines.size()).stream().map(line -> parse(line, positions)).collect(Collectors.toList());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed reading dataset " + datasetPath, ex);
        }
    }

    private TrainingRow parse(String line, Map<String, Integer> pos) {
        String[] values = line.split(",");
        return new TrainingRow(
                Double.parseDouble(values[pos.get("h1TrendSlope")].trim()),
                Double.parseDouble(values[pos.get("m15TrendSlope")].trim()),
                Double.parseDouble(values[pos.get("realizedVolatility")].trim()),
                Double.parseDouble(values[pos.get("atrPercent")].trim()),
                Double.parseDouble(values[pos.get("rangeCompression")].trim()),
                Double.parseDouble(values[pos.get("volumeSpike")].trim()),
                Double.parseDouble(values[pos.get("openInterestChange")].trim()),
                Double.parseDouble(values[pos.get("fundingRate")].trim()),
                Integer.parseInt(values[pos.get("label")].trim())
        );
    }

    private DMatrix toMatrix(List<TrainingRow> rows) throws XGBoostError {
        float[] features = new float[rows.size() * FEATURE_ORDER.size()];
        float[] labels = new float[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            TrainingRow row = rows.get(i);
            int o = i * FEATURE_ORDER.size();
            features[o] = (float) row.h1TrendSlope();
            features[o + 1] = (float) row.m15TrendSlope();
            features[o + 2] = (float) row.realizedVolatility();
            features[o + 3] = (float) row.atrPercent();
            features[o + 4] = (float) row.rangeCompression();
            features[o + 5] = (float) row.volumeSpike();
            features[o + 6] = (float) row.openInterestChange();
            features[o + 7] = (float) row.fundingRate();
            labels[i] = row.label();
        }
        DMatrix matrix = new DMatrix(features, rows.size(), FEATURE_ORDER.size(), Float.NaN);
        matrix.setLabel(labels);
        return matrix;
    }

    private Map<String, Object> params() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("objective", "multi:softprob");
        params.put("num_class", 3);
        params.put("eval_metric", "mlogloss");
        params.put("eta", 0.08);
        params.put("max_depth", 4);
        params.put("subsample", 0.9);
        params.put("colsample_bytree", 0.9);
        params.put("seed", 7);
        return params;
    }

    private Map<String, Object> metadata(String version, Map<String, Double> metrics) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("modelName", "xgboost-regime-classifier");
        metadata.put("version", version);
        metadata.put("trainedAtUtc", Instant.now().toString());
        metadata.put("featureOrder", FEATURE_ORDER);
        metadata.put("objective", "multi:softprob");
        metadata.put("metrics", metrics);
        metadata.put("binaryFile", aiProperties.getRegimeModel().getBinaryFile());
        return metadata;
    }

    private Map<String, Double> evaluate(List<TrainingRow> rows, float[][] predictions) {
        double correct = 0.0;
        double logLoss = 0.0;
        for (int i = 0; i < rows.size(); i++) {
            int label = rows.get(i).label();
            float[] probs = predictions[i];
            int winner = 0;
            float best = probs[0];
            for (int j = 1; j < probs.length; j++) {
                if (probs[j] > best) {
                    best = probs[j];
                    winner = j;
                }
            }
            if (winner == label) {
                correct++;
            }
            double p = Math.max(1e-6, Math.min(1.0 - 1e-6, probs[label]));
            logLoss += -Math.log(p);
        }

        return Map.of(
                "accuracy", correct / rows.size(),
                "logLoss", logLoss / rows.size(),
                "validationRows", (double) rows.size()
        );
    }

    public record TrainingRow(
            double h1TrendSlope,
            double m15TrendSlope,
            double realizedVolatility,
            double atrPercent,
            double rangeCompression,
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

