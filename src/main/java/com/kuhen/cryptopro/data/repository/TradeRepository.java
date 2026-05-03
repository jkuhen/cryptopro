package com.kuhen.cryptopro.data.repository;

import com.kuhen.cryptopro.data.entity.TradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<TradeEntity, Long> {

    @Query("SELECT t FROM TradeEntity t WHERE t.status = 'OPEN' ORDER BY t.createdAt DESC")
    List<TradeEntity> findAllOpen();

    @Query("SELECT t FROM TradeEntity t WHERE t.status <> 'OPEN' ORDER BY t.closedAt DESC")
    List<TradeEntity> findAllClosed();

    @Query("SELECT COUNT(t) FROM TradeEntity t WHERE t.status = 'OPEN'")
    int countOpen();

    @Query("SELECT t FROM TradeEntity t WHERE t.instrumentId = :instrumentId AND t.status = 'OPEN'")
    List<TradeEntity> findOpenByInstrumentId(@Param("instrumentId") Long instrumentId);
}

