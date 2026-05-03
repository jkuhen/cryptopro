package com.kuhen.cryptopro.tools;

import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Standalone utility that generates a synthetic XGBoost signal-scorer model artifact
 * at models/signal_scorer/v1/model.bin so the application can load the real model
 * instead of falling back to deterministic scoring.
 *
 * Run once with:
 *   mvn exec:java -Dexec.mainClass=com.kuhen.cryptopro.tools.GenerateModelArtifact
 */
public class GenerateModelArtifact {

    // Must match DEFAULT_FEATURE_ORDER in XgboostArtifactSignalScoringModel
    private static final String[] FEATURES = {
        "trendScore",
        "rsi",
        "volumeSpike",
        "openInterestChange",
        "fundingRate"
    };

    private static final int FEATURE_COUNT = FEATURES.length;
    private static final int ROWS = 6000;
    private static final long SEED = 42L;

    public static void main(String[] args) throws Exception {
        String artifactRoot = args.length > 0 ? args[0] : "models/signal_scorer";
        String version      = args.length > 1 ? args[1] : "v1";

        Path versionDir = Paths.get(artifactRoot, version);
        Files.createDirectories(versionDir);
        Path modelPath    = versionDir.resolve("model.bin");
        Path metadataPath = versionDir.resolve("metadata.json");

        System.out.println("Generating synthetic training data (" + ROWS + " rows)…");
        float[][] raw = generateSyntheticData(ROWS, SEED);
        float[]   features = raw[0];   // flat row-major feature matrix
        float[]   labels   = raw[1];

        System.out.println("Building DMatrix…");
        DMatrix dmatrix = new DMatrix(features, ROWS, FEATURE_COUNT, Float.NaN);
        dmatrix.setLabel(labels);

        Map<String, Object> params = new HashMap<>();
        params.put("objective",        "binary:logistic");
        params.put("eval_metric",      "logloss");
        params.put("max_depth",        4);
        params.put("eta",              0.06f);
        params.put("subsample",        0.9f);
        params.put("colsample_bytree", 0.85f);
        params.put("seed",             (int) SEED);
        params.put("nthread",          1);   // deterministic

        int rounds = 180;
        System.out.println("Training XGBoost (" + rounds + " rounds)…");
        Map<String, DMatrix> watches = new HashMap<>();
        Booster booster = XGBoost.train(dmatrix, params, rounds, watches, null, null);

        System.out.println("Saving model → " + modelPath.toAbsolutePath());
        booster.saveModel(modelPath.toString());

        // Quick evaluation
        float[][] preds = booster.predict(dmatrix);
        double rocAuc = approximateAuc(labels, preds, ROWS);
        double logLoss = computeLogLoss(labels, preds, ROWS);

        System.out.println("Writing metadata → " + metadataPath.toAbsolutePath());
        String metadata = buildMetadata(version, rocAuc, logLoss, ROWS);
        Files.writeString(metadataPath, metadata);

        System.out.println();
        System.out.println("Done.");
        System.out.printf("  ROC-AUC  : %.4f%n", rocAuc);
        System.out.printf("  Log-loss : %.4f%n", logLoss);
        System.out.println("  Model    : " + modelPath.toAbsolutePath());
        System.out.println("  Metadata : " + metadataPath.toAbsolutePath());
    }

    // ─── Synthetic data generator (mirrors train_signal_xgboost.py) ──────────

    private static float[][] generateSyntheticData(int rows, long seed) {
        Random rng = new Random(seed);

        float[] featMatrix = new float[rows * FEATURE_COUNT];
        float[] labelVec   = new float[rows];

        for (int i = 0; i < rows; i++) {
            double trend      = rng.nextDouble() * 2.0 - 1.0;                         // uniform(-1, 1)
            double rsi        = Math.max(5.0, Math.min(95.0, 50.0 + trend * 18.0 + rng.nextGaussian() * 7.5));
            double volumeSpike = gammaApprox(rng, 1.8, 0.9);                          // gamma(1.8, 0.9)
            double oiChange   = rng.nextGaussian() * 2.0;
            double funding    = rng.nextGaussian() * 0.01;

            double logit = -0.15
                    + 1.2  * trend
                    + 0.60 * ((rsi - 50.0) / 50.0)
                    + 0.25 * Math.min(Math.max(volumeSpike, 0.0), 4.0)
                    + 0.14 * oiChange
                    - 4.0  * Math.abs(funding);

            double prob = 1.0 / (1.0 + Math.exp(-logit));
            prob = Math.max(0.02, Math.min(0.98, prob));

            int base = i * FEATURE_COUNT;
            featMatrix[base]     = (float) trend;
            featMatrix[base + 1] = (float) rsi;
            featMatrix[base + 2] = (float) volumeSpike;
            featMatrix[base + 3] = (float) oiChange;
            featMatrix[base + 4] = (float) funding;

            labelVec[i] = rng.nextDouble() < prob ? 1.0f : 0.0f;
        }

        return new float[][]{ featMatrix, labelVec };
    }

    /** Crude gamma approximation via sum of exponentials (shape < ~10, scale arbitrary). */
    private static double gammaApprox(Random rng, double shape, double scale) {
        int intShape = Math.max(1, (int) shape);
        double sum = 0.0;
        for (int k = 0; k < intShape; k++) {
            sum -= Math.log(Math.max(1e-15, rng.nextDouble()));
        }
        return sum * scale;
    }

    // ─── Metrics helpers ─────────────────────────────────────────────────────

    private static double approximateAuc(float[] labels, float[][] preds, int rows) {
        // Simple trapezoid approximation
        int positives = 0;
        int negatives = 0;
        for (float l : labels) { if (l > 0.5f) positives++; else negatives++; }
        if (positives == 0 || negatives == 0) return 0.5;

        // Sort by prediction descending
        Integer[] idx = new Integer[rows];
        for (int i = 0; i < rows; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Float.compare(preds[b][0], preds[a][0]));

        long concordant = 0;
        long tpSoFar = 0;
        for (int rank = 0; rank < rows; rank++) {
            int i = idx[rank];
            if (labels[i] > 0.5f) {
                tpSoFar++;
            } else {
                concordant += tpSoFar;
            }
        }
        return (double) concordant / ((long) positives * negatives);
    }

    private static double computeLogLoss(float[] labels, float[][] preds, int rows) {
        double sum = 0.0;
        for (int i = 0; i < rows; i++) {
            double p = Math.max(1e-7, Math.min(1.0 - 1e-7, preds[i][0]));
            double y = labels[i];
            sum -= (y * Math.log(p) + (1.0 - y) * Math.log(1.0 - p));
        }
        return sum / rows;
    }

    // ─── Metadata JSON builder ────────────────────────────────────────────────

    private static String buildMetadata(String version, double rocAuc, double logLoss, int rows) {
        return "{\n"
                + "  \"modelName\": \"xgboost-artifact\",\n"
                + "  \"version\": \"" + version + "\",\n"
                + "  \"trainedAtUtc\": \"" + Instant.now() + "\",\n"
                + "  \"featureOrder\": [\n"
                + "    \"trendScore\",\n"
                + "    \"rsi\",\n"
                + "    \"volumeSpike\",\n"
                + "    \"openInterestChange\",\n"
                + "    \"fundingRate\"\n"
                + "  ],\n"
                + "  \"objective\": \"binary:logistic\",\n"
                + "  \"metrics\": {\n"
                + "    \"rocAuc\": " + String.format("%.6f", rocAuc) + ",\n"
                + "    \"logLoss\": " + String.format("%.6f", logLoss) + ",\n"
                + "    \"validationRows\": " + rows + "\n"
                + "  },\n"
                + "  \"binaryFile\": \"model.bin\"\n"
                + "}\n";
    }
}


