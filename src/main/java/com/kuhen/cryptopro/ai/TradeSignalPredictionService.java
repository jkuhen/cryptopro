package com.kuhen.cryptopro.ai;

import com.kuhen.cryptopro.data.entity.AiSignalPredictionEntity;
import com.kuhen.cryptopro.data.repository.AiSignalPredictionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TradeSignalPredictionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeSignalPredictionService.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final SignalScoringModel signalScoringModel;
    private final AiSignalPredictionRepository predictionRepository;

    public TradeSignalPredictionService(
            SignalScoringModel signalScoringModel,
            AiSignalPredictionRepository predictionRepository) {
        this.signalScoringModel = signalScoringModel;
        this.predictionRepository = predictionRepository;
    }

    @Transactional
    public SignalScoringResult scoreAndPersist(String symbol, Long signalId, SignalScoringFeatures features, Instant predictedAt) {
        SignalScoringResult result = signalScoringModel.score(features);
        Long instrumentId = findInstrumentId(symbol);
        if (instrumentId == null) {
            LOGGER.warn("Unable to persist AI prediction because instrument was not found for symbol={}", symbol);
            return result;
        }

        AiSignalPredictionEntity entity = new AiSignalPredictionEntity();
        entity.setInstrumentId(instrumentId);
        entity.setSignalId(signalId);
        entity.setPredictedAt(predictedAt == null ? Instant.now() : predictedAt);
        entity.setTrendScore(features.trendScore());
        entity.setRsi(features.rsi());
        entity.setVolumeSpike(features.volumeSpike());
        entity.setOpenInterestChange(features.openInterestChange());
        entity.setFundingRate(features.fundingRate());
        entity.setProbabilityScore(result.probabilityOfSuccess());
        entity.setConfidenceScore(result.confidence());
        entity.setModelName(result.modelName());
        entity.setModelVersion(result.modelVersion());
        entity.setNotes(result.notes());
        predictionRepository.save(entity);
        return result;
    }

    public List<AiSignalPredictionEntity> recentPredictions(String symbol, int limit) {
        Long instrumentId = findInstrumentId(symbol);
        if (instrumentId == null) {
            return List.of();
        }
        return predictionRepository.findRecentByInstrument(instrumentId, Math.max(1, limit));
    }

    private Long findInstrumentId(String symbol) {
        try {
            Object result = entityManager.createNativeQuery("""
                    SELECT id FROM instrument
                    WHERE exchange_name = 'BINANCE' AND symbol = ?1
                    LIMIT 1
                    """)
                    .setParameter(1, symbol)
                    .getSingleResult();
            return ((Number) result).longValue();
        } catch (Exception ex) {
            LOGGER.debug("Could not find instrument for symbol={}", symbol, ex);
            return null;
        }
    }
}

