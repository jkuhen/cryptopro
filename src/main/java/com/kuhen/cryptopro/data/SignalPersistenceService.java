package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.data.entity.SignalEntity;
import com.kuhen.cryptopro.data.repository.SignalsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service for persisting trading signals to the database.
 *
 * <p>Handles signal generation and storage with duplicate detection
 * to prevent multiple signals at the same timestamp for the same instrument/timeframe.
 */
@Service
public class SignalPersistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignalPersistenceService.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final SignalsRepository repository;

    public SignalPersistenceService(SignalsRepository repository) {
        this.repository = repository;
    }

    /**
     * Persists a single trading signal.
     *
     * <p>Checks if a signal already exists for the same instrument/timeframe at the same timestamp
     * (rounded to the minute) to prevent duplicate signals.
     *
     * @param instrumentId   the instrument ID
     * @param timeframe      the timeframe (M1, M5, M15, H1)
     * @param signalType     the signal type (BUY or SELL)
     * @param confidenceScore the confidence score (0.0 to 1.0)
     * @param recordedAt     the timestamp when the signal was generated
     * @return the persisted signal entity, or null if a duplicate was detected
     */
    @Transactional
    public SignalEntity persistSignal(Long instrumentId,
                                      SignalEntity.TimeframeEnum timeframe,
                                      SignalEntity.SignalTypeEnum signalType,
                                      Double confidenceScore,
                                      Instant recordedAt) {
        return persistSignal(instrumentId, timeframe, signalType, confidenceScore, recordedAt,
                null, null, null);
    }

    /**
     * Persists a single trading signal with projected execution levels.
     *
     * <p>Checks if a signal already exists for the same instrument/timeframe at the same timestamp
     * (rounded to the minute) to prevent duplicate signals.
     *
     * @param instrumentId   the instrument ID
     * @param timeframe      the timeframe (M1, M5, M15, H1)
     * @param signalType     the signal type (BUY or SELL)
     * @param confidenceScore the confidence score (0.0 to 1.0)
     * @param recordedAt     the timestamp when the signal was generated
     * @param entryPrice     projected entry price (ATR-based), may be {@code null}
     * @param stopLoss       projected stop-loss price (ATR-based), may be {@code null}
     * @param takeProfit     projected take-profit price (ATR-based), may be {@code null}
     * @return the persisted signal entity, or null if a duplicate was detected
     */
    @Transactional
    public SignalEntity persistSignal(Long instrumentId,
                                      SignalEntity.TimeframeEnum timeframe,
                                      SignalEntity.SignalTypeEnum signalType,
                                      Double confidenceScore,
                                      Instant recordedAt,
                                      Double entryPrice,
                                      Double stopLoss,
                                      Double takeProfit) {
        // Validate inputs
        if (instrumentId == null || instrumentId <= 0) {
            LOGGER.warn("Invalid instrumentId: {}", instrumentId);
            return null;
        }

        if (timeframe == null) {
            LOGGER.warn("Timeframe cannot be null");
            return null;
        }

        if (signalType == null) {
            LOGGER.warn("SignalType cannot be null");
            return null;
        }

        if (confidenceScore == null || confidenceScore < 0.0 || confidenceScore > 1.0) {
            LOGGER.warn("Invalid confidenceScore: {}", confidenceScore);
            return null;
        }

        if (recordedAt == null) {
            recordedAt = Instant.now();
        }

        // Check for duplicate signal at same timestamp
        boolean exists = repository.existsAtTime(instrumentId, timeframe.name(), recordedAt);
        if (exists) {
            LOGGER.debug("Signal already exists for instrument={} timeframe={} at {}",
                instrumentId, timeframe, recordedAt);
            return null;
        }

        // Persist the signal
        SignalEntity signal = new SignalEntity(instrumentId, timeframe, signalType, confidenceScore);
        signal.setCreatedAt(recordedAt);
        signal.setEntryPrice(entryPrice);
        signal.setStopLoss(stopLoss);
        signal.setTakeProfit(takeProfit);
        SignalEntity saved = repository.save(signal);

        LOGGER.info("Persisted signal: {} {} at {} with confidence={}",
            signalType, timeframe, recordedAt, confidenceScore);

        return saved;
    }

    /**
     * Returns {@code true} when a signal already exists for the same instrument/timeframe
     * in the same minute bucket as {@code recordedAt}.
     */
    @Transactional(readOnly = true)
    public boolean isDuplicateAtMinute(Long instrumentId,
                                       SignalEntity.TimeframeEnum timeframe,
                                       Instant recordedAt) {
        if (instrumentId == null || instrumentId <= 0 || timeframe == null || recordedAt == null) {
            return false;
        }
        return repository.existsAtTime(instrumentId, timeframe.name(), recordedAt);
    }

    /**
     * Retrieves the most recent signals for an instrument.
     *
     * @param instrumentId the instrument ID
     * @param limit the maximum number of signals to return
     * @return list of recent signals
     */
    public java.util.List<SignalEntity> getRecentSignals(Long instrumentId, int limit) {
        return repository.findRecentByInstrument(instrumentId, Math.max(1, limit));
    }

    /**
     * Retrieves signals within a time range.
     *
     * @param instrumentId the instrument ID
     * @param fromTime inclusive start time
     * @param toTime exclusive end time
     * @return list of signals in the time range
     */
    public java.util.List<SignalEntity> getSignalsInRange(Long instrumentId, Instant fromTime, Instant toTime) {
        return repository.findInRange(instrumentId, fromTime, toTime);
    }

    /**
     * Counts signals by type within a time range.
     *
     * @param signalType the signal type (BUY or SELL)
     * @param fromTime inclusive start time
     * @param toTime exclusive end time
     * @return number of signals matching the criteria
     */
    public int countSignalsByType(String signalType, Instant fromTime, Instant toTime) {
        return repository.findByTypeAndRange(signalType, fromTime, toTime).size();
    }
}

