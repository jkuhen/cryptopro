package com.kuhen.cryptopro.data.repository;

import com.kuhen.cryptopro.data.entity.SignalEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for persisting and querying trading signals.
 */
@Repository
public interface SignalsRepository extends JpaRepository<SignalEntity, Long> {

    /**
     * Finds the most recent signals for a given instrument.
     *
     * @param instrumentId the instrument ID
     * @param limit the maximum number of signals to return
     * @return list of recent signals (newest first)
     */
    @Query(value = "SELECT * FROM signals " +
            "WHERE instrument_id = ?1 " +
            "ORDER BY created_at DESC " +
            "LIMIT ?2", nativeQuery = true)
    List<SignalEntity> findRecentByInstrument(Long instrumentId, int limit);

    /**
     * Finds signals within a time range.
     *
     * @param instrumentId the instrument ID
     * @param fromTime inclusive start time
     * @param toTime exclusive end time
     * @return list of signals in the time range
     */
    @Query(value = "SELECT * FROM signals " +
            "WHERE instrument_id = ?1 " +
            "AND created_at >= ?2 " +
            "AND created_at < ?3 " +
            "ORDER BY created_at DESC", nativeQuery = true)
    List<SignalEntity> findInRange(Long instrumentId, Instant fromTime, Instant toTime);

    /**
     * Finds signals by type and time range.
     *
     * @param signalType the signal type (BUY or SELL)
     * @param fromTime inclusive start time
     * @param toTime exclusive end time
     * @return list of signals matching the criteria
     */
    @Query(value = "SELECT * FROM signals " +
            "WHERE signal_type = ?1 " +
            "AND created_at >= ?2 " +
            "AND created_at < ?3 " +
            "ORDER BY created_at DESC", nativeQuery = true)
    List<SignalEntity> findByTypeAndRange(String signalType, Instant fromTime, Instant toTime);

    /**
     * Checks if a signal already exists for the given instrument and timeframe at a specific time.
     *
     * @param instrumentId the instrument ID
     * @param timeframe the timeframe
     * @param timestamp the timestamp (rounded to minute)
     * @return true if a signal exists
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM signals " +
            "WHERE instrument_id = ?1 " +
            "AND timeframe = CAST(?2 AS timeframe_enum) " +
            "AND DATE_TRUNC('minute', created_at) = DATE_TRUNC('minute', CAST(?3 AS timestamptz))",
            nativeQuery = true)
    boolean existsAtTime(Long instrumentId, String timeframe, Instant timestamp);
}

