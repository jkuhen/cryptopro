package com.kuhen.cryptopro.ops;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class TransactionReportService {

    private final OpsTelemetryService opsTelemetryService;

    @PersistenceContext
    private EntityManager entityManager;

    public TransactionReportService(OpsTelemetryService opsTelemetryService) {
        this.opsTelemetryService = opsTelemetryService;
    }

    @Transactional(readOnly = true)
    public TransactionReportResponse buildReport(String symbol, int days, int limit, String accountId) {
        String filterSymbol = normalizeSymbol(symbol);
        String filterAccount = normalizeAccount(accountId);
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

        List<TransactionReportRow> rows = new ArrayList<>();
        rows.addAll(loadPersistedTradeRows(filterSymbol, cutoff));
        rows.addAll(loadTelemetryRows(filterSymbol, cutoff));

        if (filterAccount != null) {
            rows = rows.stream().filter(r -> filterAccount.equals(normalizeAccount(r.accountId()))).toList();
        }

        rows = rows.stream()
                .sorted(Comparator.comparing(this::activityTime).reversed())
                .toList();

        int totalTrades = rows.size();
        int executedToday = countExecutedToday(rows);
        int closedTrades = (int) rows.stream().filter(this::isClosed).count();
        int wins = (int) rows.stream().filter(this::isClosed).filter(r -> safeDouble(r.pnl()) > 0.0).count();

        double grossProfit = rows.stream().mapToDouble(r -> Math.max(0.0, safeDouble(r.pnl()))).sum();
        double grossLoss = rows.stream().mapToDouble(r -> Math.min(0.0, safeDouble(r.pnl()))).sum();
        double netPnl = rows.stream().mapToDouble(r -> safeDouble(r.pnl())).sum();
        double winRate = closedTrades == 0 ? 0.0 : round2((wins * 100.0) / closedTrades);

        List<TransactionReportRow> limitedRows = rows.stream().limit(limit).toList();

        return new TransactionReportResponse(
                Instant.now(),
                totalTrades,
                executedToday,
                closedTrades,
                winRate,
                round2(netPnl),
                round2(grossProfit),
                round2(grossLoss),
                round2(netPnl),
                limitedRows
        );
    }

    @SuppressWarnings("unchecked")
    private List<TransactionReportRow> loadPersistedTradeRows(String filterSymbol, Instant cutoff) {
        String sql = filterSymbol == null
                ? """
                SELECT t.created_at, t.closed_at, i.symbol,
                       CAST(s.signal_type AS text) AS signal_type,
                       t.direction, t.entry_price, t.exit_price, t.quantity, t.status, t.pnl
                FROM trades t
                JOIN instrument i ON i.id = t.instrument_id
                LEFT JOIN signals s ON s.id = t.signal_id
                WHERE t.created_at >= :cutoff
                ORDER BY COALESCE(t.closed_at, t.created_at) DESC
                """
                : """
                SELECT t.created_at, t.closed_at, i.symbol,
                       CAST(s.signal_type AS text) AS signal_type,
                       t.direction, t.entry_price, t.exit_price, t.quantity, t.status, t.pnl
                FROM trades t
                JOIN instrument i ON i.id = t.instrument_id
                LEFT JOIN signals s ON s.id = t.signal_id
                WHERE i.symbol = :sym AND t.created_at >= :cutoff
                ORDER BY COALESCE(t.closed_at, t.created_at) DESC
                """;

        var q = entityManager.createNativeQuery(sql).setParameter("cutoff", cutoff);
        if (filterSymbol != null) {
            q.setParameter("sym", filterSymbol);
        }

        List<Object[]> rows = (List<Object[]>) q.getResultList();
        List<TransactionReportRow> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Instant executedAt = toInstant(row[0]);
            Instant closedAt = toInstantNullable(row[1]);
            String symbol = asString(row[2]);
            String signalType = asString(row[3]);
            String direction = asString(row[4]);
            Double entryPrice = toDouble(row[5]);
            Double exitPrice = toDouble(row[6]);
            Double quantity = toDouble(row[7]);
            String status = asString(row[8]);
            Double pnl = toDouble(row[9]);

            String action = normalizeAction(signalType, direction);
            Double executedPrice = closedAt != null && exitPrice != null ? exitPrice : entryPrice;
            String outcome = normalizeOutcome(status, pnl);

            out.add(new TransactionReportRow(
                    closedAt,
                    executedAt,
                    symbol,
                    action,
                    executedPrice,
                    quantity,
                    outcome,
                    pnl == null ? 0.0 : pnl,
                    "PAPER",
                    "paper",
                    "TRADE_DB"
            ));
        }
        return out;
    }

    private List<TransactionReportRow> loadTelemetryRows(String filterSymbol, Instant cutoff) {
        List<TransactionLogEntry> telemetry = opsTelemetryService.recentTransactions(1000);
        List<TransactionReportRow> out = new ArrayList<>();

        for (TransactionLogEntry entry : telemetry) {
            if (entry.timestamp() == null || entry.timestamp().isBefore(cutoff)) {
                continue;
            }
            if (filterSymbol != null && !filterSymbol.equalsIgnoreCase(entry.symbol())) {
                continue;
            }

            String account = normalizeAccount(entry.accountId()) != null ? entry.accountId() : inferAccount(entry);
            String provider = normalizeProvider(entry.provider()) != null ? entry.provider() : inferProvider(entry);
            Double quantity = entry.filledQuantity() > 0.0 ? entry.filledQuantity() : entry.requestedQuantity();

            out.add(new TransactionReportRow(
                    null,
                    entry.timestamp(),
                    entry.symbol(),
                    normalizeAction(entry.direction(), entry.direction()),
                    entry.executedPrice(),
                    quantity,
                    asString(entry.status()),
                    0.0,
                    account,
                    provider,
                    asString(entry.source())
            ));
        }

        return out;
    }

    private int countExecutedToday(List<TransactionReportRow> rows) {
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        int count = 0;
        for (TransactionReportRow row : rows) {
            Instant ts = row.executedAt();
            if (ts == null) {
                continue;
            }
            LocalDate date = ts.atZone(ZoneOffset.UTC).toLocalDate();
            String outcome = asString(row.outcome()).toUpperCase(Locale.ROOT);
            boolean isExecutableOutcome = !outcome.equals("NOT_SENT")
                    && !outcome.equals("REJECTED")
                    && !outcome.equals("SKIPPED");
            if (date.equals(todayUtc) && isExecutableOutcome) {
                count++;
            }
        }
        return count;
    }

    private boolean isClosed(TransactionReportRow row) {
        if (row.closedAt() != null) {
            return true;
        }
        String outcome = asString(row.outcome()).toUpperCase(Locale.ROOT);
        return outcome.equals("WIN") || outcome.equals("LOSS") || outcome.equals("BREAKEVEN") || outcome.equals("CLOSED");
    }

    private Instant activityTime(TransactionReportRow row) {
        return row.closedAt() != null ? row.closedAt() : row.executedAt();
    }

    private String normalizeAction(String signalType, String direction) {
        String type = asString(signalType).toUpperCase(Locale.ROOT);
        if (Objects.equals(type, "BUY") || Objects.equals(type, "SELL")) {
            return type;
        }
        String dir = asString(direction).toUpperCase(Locale.ROOT);
        if (Objects.equals(dir, "LONG")) {
            return "BUY";
        }
        if (Objects.equals(dir, "SHORT")) {
            return "SELL";
        }
        return dir;
    }

    private String normalizeOutcome(String status, Double pnl) {
        String s = asString(status).toUpperCase(Locale.ROOT);
        if ("OPEN".equals(s)) {
            return "OPEN";
        }
        if (pnl != null) {
            if (pnl > 0.0) {
                return "WIN";
            }
            if (pnl < 0.0) {
                return "LOSS";
            }
            return "BREAKEVEN";
        }
        return s;
    }

    private String inferAccount(TransactionLogEntry entry) {
        String source = asString(entry.source()).toLowerCase(Locale.ROOT);
        String notes = asString(entry.notes()).toLowerCase(Locale.ROOT);
        if (source.contains("luno") || notes.contains("luno")) {
            return "LUNO";
        }
        if (source.contains("paper") || source.contains("simulation") || notes.contains("provider=paper")) {
            return "PAPER";
        }
        return "PAPER";
    }

    private String inferProvider(TransactionLogEntry entry) {
        String source = asString(entry.source()).toLowerCase(Locale.ROOT);
        String notes = asString(entry.notes()).toLowerCase(Locale.ROOT);
        if (source.contains("luno") || notes.contains("luno")) {
            return "luno";
        }
        if (notes.contains("provider=paper") || source.contains("simulation")) {
            return "paper";
        }
        return "paper";
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank() || "ALL".equalsIgnoreCase(symbol.trim())) {
            return null;
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeAccount(String accountId) {
        if (accountId == null || accountId.isBlank() || "ALL".equalsIgnoreCase(accountId.trim())) {
            return null;
        }
        return accountId.trim().toUpperCase(Locale.ROOT);
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.util.Date d) {
            return d.toInstant();
        }
        return Instant.EPOCH;
    }

    private static Instant toInstantNullable(Object value) {
        if (value == null) {
            return null;
        }
        return toInstant(value);
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private static double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

