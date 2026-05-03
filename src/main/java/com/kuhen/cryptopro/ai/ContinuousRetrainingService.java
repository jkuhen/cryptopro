package com.kuhen.cryptopro.ai;

import com.kuhen.cryptopro.config.AiProperties;
import com.kuhen.cryptopro.ops.SignalLogEntry;
import com.kuhen.cryptopro.ops.SignalOutcome;
import com.kuhen.cryptopro.ops.SignalTelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

@Service
public class ContinuousRetrainingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContinuousRetrainingService.class);

    private final AiProperties aiProperties;
    private final SignalTelemetryService signalTelemetryService;
    private final XgboostSignalTrainingService xgboostSignalTrainingService;

    private volatile RetrainingStatus status = new RetrainingStatus(false, null, 0, false, 0, "", "Not started");

    public ContinuousRetrainingService(
            AiProperties aiProperties,
            SignalTelemetryService signalTelemetryService,
            XgboostSignalTrainingService xgboostSignalTrainingService
    ) {
        this.aiProperties = aiProperties;
        this.signalTelemetryService = signalTelemetryService;
        this.xgboostSignalTrainingService = xgboostSignalTrainingService;
    }

    @Scheduled(fixedDelayString = "${cryptopro.ai.retraining.fixed-delay-ms:3600000}")
    public void scheduledRetrain() {
        if (!aiProperties.getRetraining().isEnabled()) {
            status = new RetrainingStatus(false, status.lastRunAt(), status.totalRuns(), status.lastRunSuccessful(), status.samplesUsed(), status.datasetPath(), "Retraining disabled");
            return;
        }
        runNow();
    }

    public synchronized RetrainingStatus runNow() {
        AiProperties.Retraining retraining = aiProperties.getRetraining();
        Instant now = Instant.now();

        List<SignalLogEntry> signals = signalTelemetryService.recent(retraining.getMaxSamples());
        List<SignalLogEntry> labeled = signals.stream()
                .filter(s -> s.outcome() == SignalOutcome.WIN || s.outcome() == SignalOutcome.LOSS)
                .toList();

        if (labeled.size() < retraining.getMinSamples()) {
            status = new RetrainingStatus(
                    retraining.isEnabled(),
                    now,
                    status.totalRuns() + 1,
                    false,
                    labeled.size(),
                    "",
                    "Not enough labeled signals for retraining"
            );
            return status;
        }

        Path datasetPath = Paths.get("models", "signal_scorer", "retraining", "signals-latest.csv");
        try {
            Files.createDirectories(datasetPath.getParent());
            writeCsv(datasetPath, labeled);

            String message = "Dataset prepared for retraining";
            if (retraining.isAutoTrainEnabled()) {
                List<XgboostSignalTrainingService.TrainingRow> rows = xgboostSignalTrainingService.loadCsv(datasetPath);
                String version = resolveAutoVersion(retraining);
                XgboostSignalTrainingService.TrainingArtifact artifact = xgboostSignalTrainingService.train(rows, version);
                aiProperties.getModel().setVersion(artifact.version());
                message = "Dataset prepared and model promoted to " + artifact.version();
                LOGGER.info("Retraining completed. Promoted signal model version {} (trainRows={}, validationRows={})",
                        artifact.version(), artifact.trainRows(), artifact.validationRows());
            }

            status = new RetrainingStatus(
                    retraining.isEnabled(),
                    now,
                    status.totalRuns() + 1,
                    true,
                    labeled.size(),
                    datasetPath.toAbsolutePath().toString(),
                    message
            );
        } catch (Exception ex) {
            LOGGER.warn("Retraining failed: {}", ex.getMessage(), ex);
            status = new RetrainingStatus(
                    retraining.isEnabled(),
                    now,
                    status.totalRuns() + 1,
                    false,
                    labeled.size(),
                    datasetPath.toAbsolutePath().toString(),
                    "Failed to prepare/train retraining dataset"
            );
        }

        return status;
    }

    public RetrainingStatus status() {
        return status;
    }

    private void writeCsv(Path path, List<SignalLogEntry> rows) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("trendScore,rsi,volumeSpike,openInterestChange,fundingRate,label\n");
        for (SignalLogEntry row : rows) {
            double trend = row.direction().name().equals("LONG") ? Math.abs(row.finalScore()) : -Math.abs(row.finalScore());
            double volumeSpike = row.volumeSpike() ? 2.0 : 0.2;
            double rsi = row.direction().name().equals("LONG") ? 58.0 : row.direction().name().equals("SHORT") ? 42.0 : 50.0;
            if (row.liquiditySweep()) {
                rsi += row.direction().name().equals("LONG") ? -4.0 : 4.0;
            }
            double oi = row.oiConfirmation() ? 1.5 : -0.5;
            double funding = 0.0;
            int label = row.outcome() == SignalOutcome.WIN ? 1 : 0;
            builder.append(trend).append(',')
                    .append(rsi).append(',')
                    .append(volumeSpike).append(',')
                    .append(oi).append(',')
                    .append(funding).append(',')
                    .append(label).append('\n');
        }
        Files.writeString(path, builder.toString());
    }

    private String resolveAutoVersion(AiProperties.Retraining retraining) {
        String prefix = retraining.getAutoVersionPrefix() == null || retraining.getAutoVersionPrefix().isBlank()
                ? "auto"
                : retraining.getAutoVersionPrefix().trim();
        return prefix + "-" + Instant.now().toString().replace(':', '-');
    }
}

