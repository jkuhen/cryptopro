package com.kuhen.cryptopro.data.repository;

import com.kuhen.cryptopro.data.entity.FeaturesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FeaturesRepository extends JpaRepository<FeaturesEntity, Long> {

    /**
     * Insert or update a features row via upsert.
     *
     * <p>The conflict target is the natural key {@code (instrument_id, timeframe, recorded_at)}.
     * On conflict all feature fields are refreshed with the latest calculated values.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO features
                (instrument_id, signal_id, timeframe, recorded_at, ema20, ema50, ema200, rsi, atr, volume_ma, created_at)
            VALUES
                (:instrumentId, :signalId, :timeframe, :recordedAt, :ema20, :ema50, :ema200, :rsi, :atr, :volumeMa, NOW())
            ON CONFLICT (instrument_id, timeframe, recorded_at) DO UPDATE SET
                signal_id = EXCLUDED.signal_id,
                ema20 = EXCLUDED.ema20,
                ema50 = EXCLUDED.ema50,
                ema200 = EXCLUDED.ema200,
                rsi = EXCLUDED.rsi,
                atr = EXCLUDED.atr,
                volume_ma = EXCLUDED.volume_ma
            """)
    void upsert(
            @Param("instrumentId") Long instrumentId,
            @Param("signalId")     Long signalId,
            @Param("timeframe")    String timeframe,
            @Param("recordedAt")   Instant recordedAt,
            @Param("ema20")        Double ema20,
            @Param("ema50")        Double ema50,
            @Param("ema200")       Double ema200,
            @Param("rsi")          Double rsi,
            @Param("atr")          Double atr,
            @Param("volumeMa")     Double volumeMa
    );

    /**
     * Returns the last {@code limit} feature rows for a given instrument,
     * ordered from oldest to newest.
     */
    @Query("""
            SELECT f FROM FeaturesEntity f
            WHERE f.instrumentId = :instrumentId
            ORDER BY f.recordedAt DESC
            LIMIT :limit
            """)
    List<FeaturesEntity> findLatest(
            @Param("instrumentId") Long instrumentId,
            @Param("limit")        int limit
    );

    /**
     * Returns the most recent feature row for a given instrument and timeframe.
     */
    @Query("""
            SELECT f FROM FeaturesEntity f
            WHERE f.instrumentId = :instrumentId AND f.timeframe = :timeframe
            ORDER BY f.recordedAt DESC
            LIMIT 1
            """)
    Optional<FeaturesEntity> findLatestByTimeframe(
            @Param("instrumentId") Long instrumentId,
            @Param("timeframe")    String timeframe
    );

    /**
     * Find the most recent feature row for a given instrument and timeframe before or at the given timestamp.
     */
    @Query("""
            SELECT f FROM FeaturesEntity f
            WHERE f.instrumentId = :instrumentId AND f.timeframe = :timeframe AND f.recordedAt <= :recordedAt
            ORDER BY f.recordedAt DESC
            LIMIT 1
            """)
    Optional<FeaturesEntity> findLatestBefore(
            @Param("instrumentId") Long instrumentId,
            @Param("timeframe")    String timeframe,
            @Param("recordedAt")   Instant recordedAt
    );

    /**
     * Find all feature rows for an instrument within a time range.
     */
    @Query("""
            SELECT f FROM FeaturesEntity f
            WHERE f.instrumentId = :instrumentId
                AND f.recordedAt >= :fromTime
                AND f.recordedAt < :toTime
            ORDER BY f.recordedAt ASC
            """)
    List<FeaturesEntity> findRange(
            @Param("instrumentId") Long instrumentId,
            @Param("fromTime")     Instant fromTime,
            @Param("toTime")       Instant toTime
    );

    /**
     * Count feature rows for a given instrument.
     */
    long countByInstrumentId(Long instrumentId);
}

