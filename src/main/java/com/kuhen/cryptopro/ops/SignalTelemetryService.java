package com.kuhen.cryptopro.ops;

import com.kuhen.cryptopro.ai.MarketRegime;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Service
public class SignalTelemetryService {

    private static final int  MAX_ENTRIES = 2000;
    /**
     * Hard upper bound on open-signal age.  Any signal older than this is
     * automatically expired to Missed Trades regardless of {@code staleMinutes},
     * because price has moved too far for the signal to remain actionable.
     */
    private static final long OPEN_SIGNAL_MAX_AGE_MINUTES = 10L;

    private final Deque<SignalLogEntry> signals = new ArrayDeque<>();

    public synchronized void record(SignalLogEntry entry) {
        signals.addFirst(entry);
        while (signals.size() > MAX_ENTRIES) {
            signals.removeLast();
        }
    }

    public synchronized void markTradeClosed(String tradeId, SignalOutcome outcome, String notesSuffix) {
        if (tradeId == null || tradeId.isBlank() || outcome == null) {
            return;
        }
        List<SignalLogEntry> updated = new ArrayList<>(signals.size());
        for (SignalLogEntry entry : signals) {
            if (tradeId.equals(entry.tradeId())) {
                updated.add(new SignalLogEntry(
                        entry.timestamp(),
                        entry.symbol(),
                        entry.direction(),
                        entry.finalScore(),
                        entry.aiProbability(),
                        entry.liquiditySweep(),
                        entry.volumeSpike(),
                        entry.oiConfirmation(),
                        entry.regime(),
                        entry.regimeConfidence(),
                        entry.entryPrice(),
                        entry.stopLoss(),
                        entry.takeProfit(),
                        outcome,
                        appendNotes(entry.notes(), notesSuffix),
                        entry.executionStatus(),
                        entry.accountCode(),
                        entry.provider(),
                        entry.tradeId()
                ));
            } else {
                updated.add(entry);
            }
        }
        signals.clear();
        updated.forEach(signals::addLast);
    }

    public synchronized List<SignalLogEntry> recent(int limit) {
        List<SignalLogEntry> list = new ArrayList<>();
        int count = 0;
        for (SignalLogEntry entry : signals) {
            if (count++ >= limit) {
                break;
            }
            list.add(entry);
        }
        return list;
    }

    public synchronized SignalSummaryResponse buildSummary(int limit) {
        List<SignalLogEntry> snapshot = recent(limit);

        int total = 0;
        int wins = 0;
        int losses = 0;

        ConditionCounter liquiditySweep = new ConditionCounter("liquiditySweep");
        ConditionCounter volumeSpike = new ConditionCounter("volumeSpike");
        ConditionCounter oiConfirmation = new ConditionCounter("oiConfirmation");

        for (SignalLogEntry entry : signals) {
            if (entry.outcome() == SignalOutcome.WIN || entry.outcome() == SignalOutcome.LOSS) {
                total++;
                if (entry.outcome() == SignalOutcome.WIN) {
                    wins++;
                } else {
                    losses++;
                }
            }

            if (entry.liquiditySweep()) {
                liquiditySweep.add(entry.outcome());
            }
            if (entry.volumeSpike()) {
                volumeSpike.add(entry.outcome());
            }
            if (entry.oiConfirmation()) {
                oiConfirmation.add(entry.outcome());
            }
        }

        double overallWinRate = total == 0 ? 0.0 : round((wins * 100.0) / total);

        return new SignalSummaryResponse(
                total,
                wins,
                losses,
                overallWinRate,
                List.of(liquiditySweep.toRate(), volumeSpike.toRate(), oiConfirmation.toRate()),
                snapshot
        );
    }

    public synchronized LatestRegimeResponse latestRegimeForSymbol(String symbol) {
        String requestedSymbol = symbol == null || symbol.isBlank() ? "BTCUSDT" : symbol.trim().toUpperCase();
        for (SignalLogEntry entry : signals) {
            if (requestedSymbol.equalsIgnoreCase(entry.symbol())) {
                return new LatestRegimeResponse(
                        requestedSymbol,
                        true,
                        entry.regime(),
                        clamp(entry.regimeConfidence(), 0.0, 1.0),
                        entry.timestamp(),
                        entry.notes()
                );
            }
        }

        return new LatestRegimeResponse(
                requestedSymbol,
                false,
                MarketRegime.RANGING,
                0.0,
                null,
                "No regime telemetry available for symbol"
        );
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String appendNotes(String base, String suffix) {
        String left = base == null ? "" : base.trim();
        String right = suffix == null ? "" : suffix.trim();
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        return left + " | " + right;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Builds a signal oversight report for the dashboard Open Signals and Missed Trades cards.
     *
     * @param symbol       symbol filter – pass null, blank, or "ALL" for all symbols
     * @param days         lookback window in days
     * @param limit        maximum rows per list
     * @param staleMinutes age threshold (minutes) after which an unresolved signal is "missed"
     */
    public synchronized SignalOversightResponse buildOversightReport(
            String symbol, int days, int limit, int staleMinutes) {

        final boolean allSymbols = symbol == null || symbol.isBlank()
                || "ALL".equalsIgnoreCase(symbol.trim());
        final String filterSymbol = allSymbols ? null : symbol.trim().toUpperCase();
        final Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        final Instant now = Instant.now();

        List<SignalOversightResponse.OpenSignalView> openSignals = new ArrayList<>();
        List<SignalOversightResponse.MissedTradeView> missedTrades = new ArrayList<>();

        for (SignalLogEntry entry : signals) {
            if (entry.timestamp().isBefore(cutoff)) {
                continue;
            }
            if (filterSymbol != null && !filterSymbol.equalsIgnoreCase(entry.symbol())) {
                continue;
            }
            // Skip only actually-traded outcomes (EXECUTED = open trade, WIN/LOSS = closed trade).
            // SKIPPED and REJECTED signals were never executed and can still surface as missed if aged out.
            if (entry.outcome() == SignalOutcome.EXECUTED
                    || entry.outcome() == SignalOutcome.WIN
                    || entry.outcome() == SignalOutcome.LOSS) {
                continue;
            }

            long ageMinutes = ChronoUnit.MINUTES.between(entry.timestamp(), now);

            // Derive reason
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
                // Still within the execution window — "open"
                if (openSignals.size() < limit) {
                    openSignals.add(new SignalOversightResponse.OpenSignalView(
                            entry.timestamp(),
                            entry.symbol(),
                            entry.direction() != null ? entry.direction().name() : "",
                            entry.finalScore(),
                            entry.entryPrice(),
                            entry.stopLoss(),
                            entry.takeProfit(),
                            ageMinutes,
                            entry.executionStatus() != null ? entry.executionStatus() : "PENDING",
                            reason
                    ));
                }
            } else {
                // Execution window elapsed or price-moved expiry — "missed"
                String missedReason = tooOld && !forceMissed
                        ? "Signal expired – price moved (>" + OPEN_SIGNAL_MAX_AGE_MINUTES + " min)"
                        : reason;
                if (missedTrades.size() < limit) {
                    missedTrades.add(new SignalOversightResponse.MissedTradeView(
                            entry.timestamp(),
                            entry.symbol(),
                            "M15",  // timeframe not stored in telemetry; use default
                            entry.direction() != null ? entry.direction().name() : "",
                            entry.finalScore(),
                            entry.entryPrice(),
                            "MISSED",
                            missedReason,
                            entry.notes()
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

    private static class ConditionCounter {
        private final String name;
        private int total;
        private int wins;
        private int losses;

        private ConditionCounter(String name) {
            this.name = name;
        }

        private void add(SignalOutcome outcome) {
            if (outcome == SignalOutcome.WIN || outcome == SignalOutcome.LOSS) {
                total++;
                if (outcome == SignalOutcome.WIN) {
                    wins++;
                } else {
                    losses++;
                }
            }
        }

        private ConditionWinRate toRate() {
            double winRate = total == 0 ? 0.0 : Math.round(((wins * 100.0) / total) * 100.0) / 100.0;
            return new ConditionWinRate(name, total, wins, losses, winRate);
        }
    }
}

