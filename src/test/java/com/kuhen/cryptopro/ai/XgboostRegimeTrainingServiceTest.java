package com.kuhen.cryptopro.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuhen.cryptopro.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class XgboostRegimeTrainingServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void trainsRegimeClassifierAndWritesArtifacts() {
        AiProperties properties = new AiProperties();
        properties.getRegimeModel().setArtifactRoot(tempDir.toString());
        XgboostRegimeTrainingService service = new XgboostRegimeTrainingService(properties, new ObjectMapper());

        List<XgboostRegimeTrainingService.TrainingRow> rows = java.util.stream.IntStream.range(0, 90)
                .mapToObj(i -> {
                    int cls = i % 3;
                    if (cls == 0) {
                        return new XgboostRegimeTrainingService.TrainingRow(0.72, 0.55, 0.006, 0.008, 0.62, 1.2, 0.8, -0.001, 0);
                    }
                    if (cls == 1) {
                        return new XgboostRegimeTrainingService.TrainingRow(0.05, 0.03, 0.003, 0.004, 0.25, 0.4, 0.2, 0.0001, 1);
                    }
                    return new XgboostRegimeTrainingService.TrainingRow(0.15, 0.10, 0.020, 0.017, 0.08, 2.4, 1.9, 0.002, 2);
                })
                .toList();

        XgboostRegimeTrainingService.TrainingArtifact artifact = service.train(rows, "unit-v1");

        assertTrue(Files.exists(artifact.modelPath()));
        assertTrue(Files.exists(artifact.metadataPath()));
        assertTrue(artifact.metrics().containsKey("accuracy"));
    }
}

