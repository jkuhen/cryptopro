package com.kuhen.cryptopro.ops;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class TrendReadinessService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public TrendReadinessResponse buildReport(int hours, Collection<String> configuredSymbols) {
        int safeHours = Math.max(1, Math.min(hours, 24 * 30));
        Instant cutoff = Instant.now().minus(safeHours, ChronoUnit.HOURS);

        Map<String, MutableMetrics> bySymbol = new LinkedHashMap<>();

        Set<String> requestedSymbols = new LinkedHashSet<>();
        if (configuredSymbols != null) {
            for (String symbol : configuredSymbols) {
                String normalized = normalizeSymbol(symbol);
                if (normalized != null) {
                    requestedSymbols.add(normalized);
                }
            }
        }
        for (String symbol : requestedSymbols) {
            bySymbol.putIfAbsent(symbol, new MutableMetrics(symbol));
        }

        loadH1Candles(cutoff, bySymbol);
        loadH1Indicators(cutoff, bySymbol);
        loadFirstH1Ema200(bySymbol);

        List<TrendReadinessRow> rows = new ArrayList<>();
        for (String symbol : new TreeSet<>(bySymbol.keySet())) {
            MutableMetrics m = bySymbol.get(symbol);
            double coverage = m.h1CandlesLastHours <= 0
                    ? 0.0
                    : round2((m.h1IndicatorsLastHours * 100.0) / m.h1CandlesLastHours);
            rows.add(new TrendReadinessRow(
                    symbol,
                    m.h1CandlesLastHours,
                    m.h1IndicatorsLastHours,
                    coverage,
                    m.h1Ema200ReadyLastHours,
                    m.firstH1Ema200At,
                    deriveStatus(m, coverage)
            ));
        }

        return new TrendReadinessResponse(Instant.now(), safeHours, rows);
    }

    @SuppressWarnings("unchecked")
    private void loadH1Candles(Instant cutoff, Map<String, MutableMetrics> bySymbol) {
        String sql = """
                SELECT c.symbol, COUNT(*) AS candles
                FROM ohlcv_candle c
                WHERE CAST(c.timeframe AS text) = 'H1'
                  AND c.open_time >= :cutoff
                GROUP BY c.symbol
                """;

        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("cutoff", cutoff)
                .getResultList();

        for (Object[] row : rows) {
            String symbol = normalizeSymbol(row[0]);
            if (symbol == null) continue;
            MutableMetrics metrics = bySymbol.computeIfAbsent(symbol, MutableMetrics::new);
            metrics.h1CandlesLastHours = asLong(row[1]);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadH1Indicators(Instant cutoff, Map<String, MutableMetrics> bySymbol) {
        String sql = """
                SELECT i.symbol,
                       COUNT(*) AS indicators,
                       SUM(CASE WHEN f.ema200 IS NOT NULL THEN 1 ELSE 0 END) AS ema200_ready
                FROM features f
                JOIN instrument i ON i.id = f.instrument_id
                WHERE CAST(f.timeframe AS text) = 'H1'
                  AND f.recorded_at >= :cutoff
                GROUP BY i.symbol
                """;

        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("cutoff", cutoff)
                .getResultList();

        for (Object[] row : rows) {
            String symbol = normalizeSymbol(row[0]);
            if (symbol == null) continue;
            MutableMetrics metrics = bySymbol.computeIfAbsent(symbol, MutableMetrics::new);
            metrics.h1IndicatorsLastHours = asLong(row[1]);
            metrics.h1Ema200ReadyLastHours = asLong(row[2]);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFirstH1Ema200(Map<String, MutableMetrics> bySymbol) {
        String sql = """
                SELECT i.symbol, MIN(f.recorded_at) AS first_h1_ema200_at
                FROM features f
                JOIN instrument i ON i.id = f.instrument_id
                WHERE CAST(f.timeframe AS text) = 'H1'
                  AND f.ema200 IS NOT NULL
                GROUP BY i.symbol
                """;

        List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
        for (Object[] row : rows) {
            String symbol = normalizeSymbol(row[0]);
            if (symbol == null) continue;
            MutableMetrics metrics = bySymbol.computeIfAbsent(symbol, MutableMetrics::new);
            metrics.firstH1Ema200At = asInstant(row[1]);
        }
    }

    private String deriveStatus(MutableMetrics m, double coveragePct) {
        if (m.h1CandlesLastHours <= 0) {
            return "NO_CANDLES";
        }
        if (m.h1IndicatorsLastHours <= 0) {
            return "NO_INDICATORS";
        }
        if (m.h1Ema200ReadyLastHours <= 0 || m.firstH1Ema200At == null) {
            return "EMA200_NOT_READY";
        }
        if (coveragePct >= 90.0) {
            return "READY";
        }
        return "PARTIAL";
    }

    private static String normalizeSymbol(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static Instant asInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class MutableMetrics {
        private long h1CandlesLastHours;
        private long h1IndicatorsLastHours;
        private long h1Ema200ReadyLastHours;
        private Instant firstH1Ema200At;

        private MutableMetrics(String ignoredSymbol) {
        }
    }
}


