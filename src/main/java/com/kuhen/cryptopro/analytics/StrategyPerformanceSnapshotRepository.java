package com.kuhen.cryptopro.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StrategyPerformanceSnapshotRepository
        extends JpaRepository<StrategyPerformanceSnapshotEntity, Long> {

    @Query("SELECT s FROM StrategyPerformanceSnapshotEntity s WHERE s.symbol = :symbol AND s.windowLabel = :label ORDER BY s.computedAt DESC")
    List<StrategyPerformanceSnapshotEntity> findBySymbolAndWindowLabel(
            @Param("symbol") String symbol, @Param("label") String label);

    Optional<StrategyPerformanceSnapshotEntity> findTopBySymbolAndWindowLabelOrderByComputedAtDesc(
            String symbol, String label);

    @Query("SELECT s FROM StrategyPerformanceSnapshotEntity s WHERE s.symbol = :symbol ORDER BY s.computedAt DESC")
    List<StrategyPerformanceSnapshotEntity> findAllBySymbol(@Param("symbol") String symbol);
}

