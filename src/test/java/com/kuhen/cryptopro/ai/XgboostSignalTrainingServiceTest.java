package com.kuhen.cryptopro.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuhen.cryptopro.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class XgboostSignalTrainingServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void trainsModelAndWritesArtifacts() {
        AiProperties properties = new AiProperties();
        properties.getModel().setArtifactRoot(tempDir.toString());
        XgboostSignalTrainingService service = new XgboostSignalTrainingService(properties, new ObjectMapper());

        List<XgboostSignalTrainingService.TrainingRow> rows = java.util.stream.IntStream.range(0, 80)
                .mapToObj(i -> new XgboostSignalTrainingService.TrainingRow(
                        i % 2 == 0 ? 0.7 : -0.4,
                        i % 2 == 0 ? 60.0 : 38.0,
                        i % 2 == 0 ? 2.0 : 0.3,
                        i % 2 == 0 ? 1.4 : -0.8,
                        i % 2 == 0 ? -0.002 : 0.012,
                        i % 2 == 0 ? 1 : 0
                ))
                .toList();

        XgboostSignalTrainingService.TrainingArtifact artifact = service.train(rows, "unit-v1");

        assertTrue(Files.exists(artifact.modelPath()));
        assertTrue(Files.exists(artifact.metadataPath()));
        assertTrue(artifact.metrics().containsKey("accuracy"));
        assertTrue(artifact.metrics().get("validationRows") > 0.0);
    }
}

