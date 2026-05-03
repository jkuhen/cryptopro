package com.kuhen.cryptopro.preprocess;

import com.kuhen.cryptopro.data.model.Candle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PreprocessingService {

    private final SpikeCleaner spikeCleaner;
    private final VolumeNormalizer volumeNormalizer;
    private final SessionRegimeDetector sessionRegimeDetector;

    /** Default constructor for framework instantiation. */
    public PreprocessingService() {
        this.spikeCleaner = null;
        this.volumeNormalizer = null;
        this.sessionRegimeDetector = null;
    }

    @Autowired
    public PreprocessingService(
            SpikeCleaner spikeCleaner,
            VolumeNormalizer volumeNormalizer,
            SessionRegimeDetector sessionRegimeDetector
    ) {
        this.spikeCleaner = spikeCleaner;
        this.volumeNormalizer = volumeNormalizer;
        this.sessionRegimeDetector = sessionRegimeDetector;
    }

    public PreprocessingResult preprocess(List<Candle> rawCandles) {
        if (rawCandles.isEmpty()) {
            return new PreprocessingResult(List.of(), SessionType.ASIA, VolatilityRegime.NORMAL, 0.0);
        }

        List<Candle> cleanedCandles = spikeCleaner.clean(rawCandles);
        List<VolumeNormalizer.VolumePoint> normalizedVolume = volumeNormalizer.normalize(cleanedCandles);

        List<PreprocessedCandle> preprocessed = new ArrayList<>(cleanedCandles.size());
        for (int i = 0; i < cleanedCandles.size(); i++) {
            Candle raw = rawCandles.get(i);
            Candle cleaned = cleanedCandles.get(i);
            VolumeNormalizer.VolumePoint volumePoint = normalizedVolume.get(i);
            preprocessed.add(new PreprocessedCandle(
                    raw,
                    cleaned.close(),
                    volumePoint.zScore(),
                    volumePoint.relativeVolume()
            ));
        }

        SessionType session = sessionRegimeDetector.detectSessionType(rawCandles.get(rawCandles.size() - 1).openTime());
        VolatilityRegime regime = sessionRegimeDetector.detectVolatilityRegime(cleanedCandles);
        double dataQualityScore = calculateDataQualityScore(preprocessed);

        return new PreprocessingResult(preprocessed, session, regime, dataQualityScore);
    }

    private double calculateDataQualityScore(List<PreprocessedCandle> candles) {
        if (candles.isEmpty()) {
            return 0.0;
        }

        double avgRelativeVolume = candles.stream().mapToDouble(PreprocessedCandle::relativeVolume).average().orElse(1.0);
        double avgAbsZ = candles.stream().mapToDouble(c -> Math.abs(c.volumeZScore())).average().orElse(0.0);

        double volumeScore = Math.max(0.0, 1.0 - Math.abs(avgRelativeVolume - 1.0));
        double noisePenalty = Math.min(0.7, avgAbsZ * 0.15);
        return Math.max(0.0, Math.min(1.0, volumeScore + 0.3 - noisePenalty));
    }
}

