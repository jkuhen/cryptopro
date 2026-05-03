package com.kuhen.cryptopro.data.repository;

import com.kuhen.cryptopro.data.entity.AiSignalPredictionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiSignalPredictionRepository extends JpaRepository<AiSignalPredictionEntity, Long> {

    @Query(value = "SELECT * FROM ai_signal_prediction WHERE instrument_id = ?1 ORDER BY predicted_at DESC LIMIT ?2", nativeQuery = true)
    List<AiSignalPredictionEntity> findRecentByInstrument(Long instrumentId, int limit);
}

