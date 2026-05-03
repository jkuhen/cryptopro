package com.kuhen.cryptopro.ops;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Service
public class OpsTelemetryService {

    private static final int MAX_ENTRIES = 500;

    private final Instant startedAt = Instant.now();
    private final Deque<TransactionLogEntry> transactions = new ArrayDeque<>();
    private final Deque<ErrorLogEntry> errors = new ArrayDeque<>();

    public synchronized void recordTransaction(TransactionLogEntry entry) {
        String status = entry == null || entry.status() == null ? "" : entry.status().trim().toUpperCase();
        // Recent transactions should reflect only successfully executed trades.
        if (!"FILLED".equals(status) && !"PARTIAL".equals(status)) {
            return;
        }
        transactions.addFirst(entry);
        while (transactions.size() > MAX_ENTRIES) {
            transactions.removeLast();
        }
    }

    public synchronized void recordError(ErrorLogEntry entry) {
        errors.addFirst(entry);
        while (errors.size() > MAX_ENTRIES) {
            errors.removeLast();
        }
    }

    public synchronized List<TransactionLogEntry> recentTransactions(int limit) {
        List<TransactionLogEntry> list = new ArrayList<>();
        int count = 0;
        for (TransactionLogEntry entry : transactions) {
            if (count++ >= limit) {
                break;
            }
            list.add(entry);
        }
        return list;
    }

    public synchronized List<ErrorLogEntry> recentErrors(int limit) {
        List<ErrorLogEntry> list = new ArrayList<>();
        int count = 0;
        for (ErrorLogEntry entry : errors) {
            if (count++ >= limit) {
                break;
            }
            list.add(entry);
        }
        return list;
    }

    public synchronized DashboardKpis buildKpis() {
        int total = transactions.size();
        int success = 0;
        int rejected = 0;
        double slippageSum = 0.0;
        int slippageCount = 0;

        for (TransactionLogEntry entry : transactions) {
            if ("FILLED".equals(entry.status()) || "PARTIAL".equals(entry.status())) {
                success++;
            }
            if ("REJECTED".equals(entry.status()) || "NOT_SENT".equals(entry.status())) {
                rejected++;
            }
            if (entry.slippageBps() > 0.0) {
                slippageSum += entry.slippageBps();
                slippageCount++;
            }
        }

        double successRate = total == 0 ? 0.0 : (success * 100.0) / total;
        double avgSlippage = slippageCount == 0 ? 0.0 : slippageSum / slippageCount;
        return new DashboardKpis(total, success, rejected, round(successRate), round(avgSlippage), errors.size());
    }

    public long uptimeSeconds() {
        return Math.max(0L, Instant.now().getEpochSecond() - startedAt.getEpochSecond());
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

