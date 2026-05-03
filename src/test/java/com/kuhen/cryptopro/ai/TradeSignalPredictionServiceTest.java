package com.kuhen.cryptopro.ai;

import com.kuhen.cryptopro.data.entity.AiSignalPredictionEntity;
import com.kuhen.cryptopro.data.repository.AiSignalPredictionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeSignalPredictionServiceTest {

    @Mock
    private SignalScoringModel signalScoringModel;
    @Mock
    private AiSignalPredictionRepository predictionRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;

    @InjectMocks
    private TradeSignalPredictionService service;

    @Test
    void scoresAndPersistsPredictionForKnownInstrument() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        SignalScoringFeatures features = new SignalScoringFeatures(0.72, 58.0, 2.2, 1.8, -0.002);
        SignalScoringResult expected = new SignalScoringResult(0.81, 0.62, "xgboost-signal-scorer", "vtest", "artifact");
        Instant now = Instant.parse("2026-04-25T12:00:00Z");

        when(signalScoringModel.score(features)).thenReturn(expected);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(1, "BTCUSDT")).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1L);
        when(predictionRepository.save(any(AiSignalPredictionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SignalScoringResult actual = service.scoreAndPersist("BTCUSDT", 25L, features, now);

        ArgumentCaptor<AiSignalPredictionEntity> captor = ArgumentCaptor.forClass(AiSignalPredictionEntity.class);
        verify(predictionRepository).save(captor.capture());
        AiSignalPredictionEntity saved = captor.getValue();

        assertSame(expected, actual);
        assertEquals(1L, saved.getInstrumentId());
        assertEquals(25L, saved.getSignalId());
        assertEquals(now, saved.getPredictedAt());
        assertEquals(58.0, saved.getRsi());
        assertEquals(0.81, saved.getProbabilityScore());
        assertEquals("xgboost-signal-scorer", saved.getModelName());
    }
}

