package com.kuhen.cryptopro.ops;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Hybrid signal oversight service.
 *
 * <p>Merges persisted signals from the {@code signals} DB table with
 * live, in-memory telemetry entries so that both the Open Signals and
 * Missed Trades dashboard cards reflect the full signal history.
 *
 * <p>Classification rule:
 * <ul>
 *   <li><b>Open</b>  – signal age &lt; {@code staleMinutes}</li>
 *   <li><b>Missed</b> – signal age &ge; {@code staleMinutes}</li>
 * </ul>
 *
 * <p>Deduplication key: {@code (symbol, signalType, minute-truncated createdAt)}.
 * DB rows take priority; telemetry-only entries are appended afterwards.
 */
@Service
public class SignalOversightService {

    private static final Logger LOG = LoggerFactory.getLogger(SignalOversightService.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final SignalTelemetryService signalTelemetryService;

    public SignalOversightService(SignalTelemetryService signalTelemetryService) {
        this.signalTelemetryService = signalTelemetryService;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Builds the oversight report powering the Open Signals and Missed Trades cards.
     *
     * @param symbol       symbol filter – pass {@code null}, blank, or {@code "ALL"} for every symbol
     * @param days         lookback window (1–90 days)
     * @param limit        max rows per card (1–500)
     * @param staleMinutes age threshold (minutes) that moves a signal from Open → Missed
     */
    @Transactional(readOnly = true)
    public SignalOversightResponse buildOversightReport(
            String symbol, int days, int limit, int staleMinutes) {

        boolean allSymbols = symbol == null || symbol.isBlank()
                || "ALL".equalsIgnoreCase(symbol.trim());
        String filterSymbol = allSymbols ? null : symbol.trim().toUpperCase(Locale.ROOT);

        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        Instant now    = Instant.now();

        List<SignalLogEntry> telemetry = signalTelemetryService.recent(MAX_TELE);
        // Only signals that were actually traded (EXECUTED → open trade, WIN/LOSS → closed trade)
        // are considered "resolved" and excluded from the missed list.
        // SKIPPED and REJECTED signals were never traded and should still appear as missed if aged out.
        Set<String> resolvedTelemetryKeys = new HashSet<>();
        for (SignalLogEntry entry : telemetry) {
            if (entry.timestamp().isBefore(cutoff)) continue;
            if (entry.outcome() == null) continue;
            if (entry.outcome() != SignalOutcome.EXECUTED
                    && entry.outcome() != SignalOutcome.WIN
                    && entry.outcome() != SignalOutcome.LOSS) continue;
            if (filterSymbol != null && !filterSymbol.equalsIgnoreCase(entry.symbol())) continue;
            String sigType = entry.direction() != null ? entry.direction().name() : "HOLD";
            resolvedTelemetryKeys.add(dedupKey(entry.symbol(), sigType, entry.timestamp()));
        }

        // --- 1.  DB rows (primary source) -----------------------------------
        List<Object[]> dbRows = queryDb(filterSymbol, cutoff, limit * 2);

        // Dedup key set: "SYMBOL|SIGNALTYPE|epochMinute"
        Set<String> seenKeys = new LinkedHashSet<>();

        List<SignalOversightResponse.OpenSignalView>   openSignals  = new ArrayList<>();
        List<SignalOversightResponse.MissedTradeView>  missedTrades = new ArrayList<>();

        for (Object[] row : dbRows) {
            // col order: id, instrument_id, timeframe, signal_type, confidence_score, created_at,
            //            symbol, entry_price, stop_loss, take_profit, trade_id
            Instant   createdAt  = toInstant(row[5]);
            String    sym        = asString(row[6]);
            String    tf         = asString(row[2]);
            String    sigType    = asString(row[3]);
            Double    confidence = toDouble(row[4]);
            Double    entryPrice = row.length > 7 ? toDouble(row[7]) : null;
            Double    stopLoss   = row.length > 8 ? toDouble(row[8]) : null;
            Double    takeProfit = row.length > 9 ? toDouble(row[9]) : null;
            boolean   hasLinkedTrade = row.length > 10 && row[10] != null;

            String key = dedupKey(sym, sigType, createdAt);
            if (hasLinkedTrade || resolvedTelemetryKeys.contains(key)) continue;
            if (!seenKeys.add(key)) continue;

            long ageMinutes = ChronoUnit.MINUTES.between(createdAt, now);
            classifyWithTradeData(sym, tf, sigType, confidence, createdAt, ageMinutes, staleMinutes,
                     "DB", openSignals, missedTrades, limit, entryPrice, stopLoss, takeProfit);
        }

        // --- 2.  In-memory telemetry (supplement only) ----------------------
        for (SignalLogEntry entry : telemetry) {
            if (entry.timestamp().isBefore(cutoff)) continue;
            if (filterSymbol != null && !filterSymbol.equalsIgnoreCase(entry.symbol())) continue;
            // Skip only actually-traded outcomes (EXECUTED = open trade, WIN/LOSS = closed trade).
            // SKIPPED and REJECTED signals were never executed and can still appear as missed if aged out.
            if (entry.outcome() == SignalOutcome.EXECUTED
                    || entry.outcome() == SignalOutcome.WIN
                    || entry.outcome() == SignalOutcome.LOSS) continue;

            String sigType = entry.direction() != null ? entry.direction().name() : "HOLD";
            String key     = dedupKey(entry.symbol(), sigType, entry.timestamp());
            if (!seenKeys.add(key)) continue; // already covered by DB row

            long ageMinutes = ChronoUnit.MINUTES.between(entry.timestamp(), now);

            // Derive a human-readable reason based on outcome
            String reason;
            if (entry.outcome() == SignalOutcome.REJECTED) {
                reason = entry.notes() != null ? "Rejected: " + entry.notes() : "Signal rejected";
            } else if (entry.outcome() == SignalOutcome.SKIPPED) {
                reason = entry.notes() != null ? "Skipped: " + entry.notes() : "Signal skipped";
            } else {
                reason = entry.notes() != null ? entry.notes() : "Signal expired without execution";
            }

            boolean forceMissed = entry.outcome() == SignalOutcome.REJECTED
                    || entry.outcome() == SignalOutcome.SKIPPED;
            // Signals older than OPEN_SIGNAL_MAX_AGE_MINUTES are no longer actionable
            // (price has moved) and are forced into Missed Trades.
            boolean tooOld = ageMinutes >= OPEN_SIGNAL_MAX_AGE_MINUTES;

            if (!forceMissed && !tooOld && ageMinutes < staleMinutes) {
                // Still open
                if (openSignals.size() < limit) {
                    openSignals.add(new SignalOversightResponse.OpenSignalView(
                            entry.timestamp(),
                            entry.symbol(),
                            sigType,
                            entry.finalScore(),
                            entry.entryPrice(),
                            entry.stopLoss(),
                            entry.takeProfit(),
                            ageMinutes,
                            "TELEMETRY",
                            reason
                    ));
                }
            } else {
                // Aged out or too old → missed
                String missedReason = tooOld && !forceMissed
                        ? "Signal expired – price moved (>" + OPEN_SIGNAL_MAX_AGE_MINUTES + " min)"
                        : reason;
                if (missedTrades.size() < limit) {
                    missedTrades.add(new SignalOversightResponse.MissedTradeView(
                            entry.timestamp(),
                            entry.symbol(),
                            "M15",
                            sigType,
                            entry.finalScore(),
                            entry.entryPrice(),
                            "MISSED",
                            missedReason,
                            "TELEMETRY"
                    ));
                }
            }
        }

        return new SignalOversightResponse(
                openSignals,
                missedTrades,
                openSignals.size(),
                missedTrades.size(),
                0,
                List.of(),
                0.0,
                0
        );
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private static final int  MAX_TELE = 500;
    /**
     * Hard upper bound on open-signal age.  Any signal older than this is
     * automatically expired to Missed Trades regardless of {@code staleMinutes},
     * because price has moved too far for the signal to remain actionable.
     */
    private static final long OPEN_SIGNAL_MAX_AGE_MINUTES = 10L;

    /**
     * Queries the {@code signals} table joined with {@code instrument} to resolve symbols.
     * When {@code filterSymbol} is {@code null} all instruments are included.
     */
    @SuppressWarnings("unchecked")
    private List<Object[]> queryDb(String filterSymbol, Instant cutoff, int maxRows) {
        try {
            String sql = filterSymbol == null
                    ? """
                      SELECT s.id, s.instrument_id, CAST(s.timeframe AS text),
                             CAST(s.signal_type AS text), s.confidence_score, s.created_at,
                             i.symbol,
                             COALESCE(t.entry_price, s.entry_price) AS entry_price,
                             COALESCE(t.stop_loss,   s.stop_loss)   AS stop_loss,
                             COALESCE(t.take_profit, s.take_profit) AS take_profit,
                             t.id AS trade_id
                      FROM signals s
                      JOIN instrument i ON i.id = s.instrument_id
                      LEFT JOIN trades t ON t.signal_id = s.id AND t.status IN ('OPEN', 'CLOSED')
                      WHERE s.created_at >= :cutoff
                      ORDER BY s.created_at DESC
                      LIMIT :maxRows
                      """
                    : """
                      SELECT s.id, s.instrument_id, CAST(s.timeframe AS text),
                             CAST(s.signal_type AS text), s.confidence_score, s.created_at,
                             i.symbol,
                             COALESCE(t.entry_price, s.entry_price) AS entry_price,
                             COALESCE(t.stop_loss,   s.stop_loss)   AS stop_loss,
                             COALESCE(t.take_profit, s.take_profit) AS take_profit,
                             t.id AS trade_id
                      FROM signals s
                      JOIN instrument i ON i.id = s.instrument_id
                      LEFT JOIN trades t ON t.signal_id = s.id AND t.status IN ('OPEN', 'CLOSED')
                      WHERE i.symbol = :sym
                        AND s.created_at >= :cutoff
                      ORDER BY s.created_at DESC
                      LIMIT :maxRows
                      """;

            var q = entityManager.createNativeQuery(sql)
                    .setParameter("cutoff", cutoff)
                    .setParameter("maxRows", maxRows);
            if (filterSymbol != null) {
                q.setParameter("sym", filterSymbol);
            }

            return (List<Object[]>) q.getResultList();
        } catch (Exception ex) {
            LOG.warn("DB query for signal oversight failed – falling back to telemetry only", ex);
            return List.of();
        }
    }

    private void classify(
            String symbol, String timeframe, String sigType, Double confidence,
            Instant createdAt, long ageMinutes, int staleMinutes, String source,
            List<SignalOversightResponse.OpenSignalView>  open,
            List<SignalOversightResponse.MissedTradeView> missed,
            int limit) {

        boolean tooOld = ageMinutes >= OPEN_SIGNAL_MAX_AGE_MINUTES;
        if (!tooOld && ageMinutes < staleMinutes) {
            if (open.size() < limit) {
                open.add(new SignalOversightResponse.OpenSignalView(
                        createdAt,
                        symbol,
                        sigType,
                        confidence,
                        null,
                        null,
                        null,
                        ageMinutes,
                        source,
                        timeframe
                ));
            }
        } else {
            String reason = tooOld
                    ? "Signal expired – price moved (>" + OPEN_SIGNAL_MAX_AGE_MINUTES + " min)"
                    : "Signal expired without execution";
            if (missed.size() < limit) {
                missed.add(new SignalOversightResponse.MissedTradeView(
                        createdAt,
                        symbol,
                        timeframe,
                        sigType,
                        confidence,
                        null,
                        "MISSED",
                        reason,
                        source
                ));
            }
        }
    }

    private void classifyWithTradeData(
            String symbol, String timeframe, String sigType, Double confidence,
            Instant createdAt, long ageMinutes, int staleMinutes, String source,
            List<SignalOversightResponse.OpenSignalView>  open,
            List<SignalOversightResponse.MissedTradeView> missed,
            int limit,
            Double entryPrice, Double stopLoss, Double takeProfit) {

        boolean tooOld = ageMinutes >= OPEN_SIGNAL_MAX_AGE_MINUTES;
        if (!tooOld && ageMinutes < staleMinutes) {
            if (open.size() < limit) {
                open.add(new SignalOversightResponse.OpenSignalView(
                        createdAt,
                        symbol,
                        sigType,
                        confidence,
                        entryPrice,
                        stopLoss,
                        takeProfit,
                        ageMinutes,
                        source,
                        timeframe
                ));
            }
        } else {
            String reason = tooOld
                    ? "Signal expired – price moved (>" + OPEN_SIGNAL_MAX_AGE_MINUTES + " min)"
                    : "Signal expired without execution";
            if (missed.size() < limit) {
                missed.add(new SignalOversightResponse.MissedTradeView(
                        createdAt,
                        symbol,
                        timeframe,
                        sigType,
                        confidence,
                        entryPrice,
                        "MISSED",
                        reason,
                        source
                ));
            }
        }
    }

    /** Deduplication key: SYMBOL|SIGTYPE|epoch-minute */
    private static String dedupKey(String symbol, String sigType, Instant ts) {
        long epochMinute = ts == null ? 0L : ts.truncatedTo(ChronoUnit.MINUTES).getEpochSecond() / 60;
        return (symbol == null ? "" : symbol.toUpperCase(Locale.ROOT))
                + "|" + normalizeSignalAction(sigType)
                + "|" + epochMinute;
    }

    private static String normalizeSignalAction(String sigType) {
        String normalized = sigType == null ? "" : sigType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LONG" -> "BUY";
            case "SHORT" -> "SELL";
            default -> normalized;
        };
    }

    private static Instant toInstant(Object o) {
        if (o instanceof Instant i)  return i;
        if (o instanceof java.sql.Timestamp t) return t.toInstant();
        if (o instanceof java.util.Date d) return d.toInstant();
        return Instant.EPOCH;
    }

    private static String asString(Object o) {
        return o == null ? "" : o.toString();
    }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Double d) return d;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception ignored) { return null; }
    }
}

