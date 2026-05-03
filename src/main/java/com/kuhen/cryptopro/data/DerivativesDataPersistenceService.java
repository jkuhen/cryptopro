package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.data.model.FundingRate;
import com.kuhen.cryptopro.data.model.OpenInterestSnapshot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Persists normalized derivatives snapshots into {@code derivatives_data}.
 *
 * <p>Normalization is done by resolving {@code instrument_id} from the
 * {@code instrument(exchange_name, symbol)} unique key for BINANCE.
 */
@Service
public class DerivativesDataPersistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DerivativesDataPersistenceService.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public boolean persistSnapshot(String symbol,
                                   Instant syncedAt,
                                   OpenInterestSnapshot latestOpenInterest,
                                   FundingRate latestFundingRate) {
        LastKnown lastKnown = findLastKnown(symbol);

        Double openInterest = normalizeOpenInterest(symbol, latestOpenInterest, lastKnown);
        Double fundingRate = normalizeFundingRate(symbol, latestFundingRate, lastKnown);
        if (openInterest == null || fundingRate == null) {
            LOGGER.warn("Skipping derivatives persistence for {} because required data is missing (oi={}, funding={}).",
                    symbol, openInterest, fundingRate);
            return false;
        }

        Instant recordedAt = syncedAt.truncatedTo(ChronoUnit.MINUTES);
        int updated = entityManager.createNativeQuery("""
                INSERT INTO derivatives_data (instrument_id, recorded_at, open_interest, funding_rate, created_at)
                SELECT i.id, :recordedAt, :openInterest, :fundingRate, NOW()
                FROM instrument i
                WHERE i.exchange_name = 'BINANCE' AND i.symbol = :symbol
                ON CONFLICT (instrument_id, recorded_at) DO UPDATE SET
                    open_interest = EXCLUDED.open_interest,
                    funding_rate = EXCLUDED.funding_rate
                """)
                .setParameter("recordedAt", recordedAt)
                .setParameter("openInterest", openInterest)
                .setParameter("fundingRate", fundingRate)
                .setParameter("symbol", symbol)
                .executeUpdate();

        if (updated == 0) {
            LOGGER.error("No instrument mapping found for symbol {} while persisting derivatives_data.", symbol);
            return false;
        }
        return true;
    }

    private LastKnown findLastKnown(String symbol) {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                SELECT dd.open_interest, dd.funding_rate
                FROM derivatives_data dd
                JOIN instrument i ON i.id = dd.instrument_id
                WHERE i.exchange_name = 'BINANCE' AND i.symbol = :symbol
                ORDER BY dd.recorded_at DESC
                LIMIT 1
                """)
                .setParameter("symbol", symbol)
                .getResultStream()
                .findFirst()
                .orElse(null);

        if (row == null) {
            return new LastKnown(null, null);
        }
        return new LastKnown(toDouble(row[0]), toDouble(row[1]));
    }

    private Double normalizeOpenInterest(String symbol, OpenInterestSnapshot latest, LastKnown lastKnown) {
        if (latest == null || !Double.isFinite(latest.openInterestUsd()) || latest.openInterestUsd() < 0.0) {
            if (lastKnown.openInterest() != null) {
                LOGGER.warn("Open interest missing/invalid for {}. Using last known value {}.", symbol, lastKnown.openInterest());
                return lastKnown.openInterest();
            }
            LOGGER.warn("Open interest missing/invalid for {} and no historical fallback available.", symbol);
            return null;
        }

        if (latest.openInterestUsd() == 0.0) {
            LOGGER.warn("Open interest anomaly for {}: received zero open interest.", symbol);
        }
        return latest.openInterestUsd();
    }

    private Double normalizeFundingRate(String symbol, FundingRate latest, LastKnown lastKnown) {
        if (latest == null || !Double.isFinite(latest.rate())) {
            if (lastKnown.fundingRate() != null) {
                LOGGER.warn("Funding rate missing/invalid for {}. Using last known value {}.", symbol, lastKnown.fundingRate());
                return lastKnown.fundingRate();
            }
            LOGGER.warn("Funding rate missing/invalid for {} and no historical fallback available.", symbol);
            return null;
        }

        if (Math.abs(latest.rate()) > 0.10) {
            LOGGER.warn("Funding-rate anomaly for {}: unusually large value {}.", symbol, latest.rate());
        }
        return latest.rate();
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record LastKnown(Double openInterest, Double fundingRate) {
    }
}

