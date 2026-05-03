package com.kuhen.cryptopro.ops;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/v2/accounts")
public class AccountsController {

    private final AtomicLong idSequence = new AtomicLong(1L);
    private final Map<Long, AccountItem> accounts = new ConcurrentHashMap<>();
    private final List<AccountAuditEntry> auditEntries = new CopyOnWriteArrayList<>();

    @GetMapping
    public AccountsResponse listAccounts() {
        List<AccountItem> items = accounts.values().stream()
                .sorted(Comparator.comparingLong(AccountItem::id))
                .map(AccountItem::copy)
                .toList();
        return new AccountsResponse(items);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountItem createAccount(@RequestBody AccountUpsertRequest request) {
        long id = idSequence.getAndIncrement();
        AccountItem item = AccountItem.fromRequest(id, request);
        accounts.put(id, item);
        appendAudit("CREATE", item.accountCode());
        return item.copy();
    }

    @PutMapping("/{id}")
    public AccountItem updateAccount(@PathVariable long id, @RequestBody AccountUpsertRequest request) {
        AccountItem existing = accounts.get(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + id);
        }
        AccountItem updated = AccountItem.fromRequest(id, request);
        accounts.put(id, updated);
        appendAudit("UPDATE", updated.accountCode());
        return updated.copy();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable long id) {
        AccountItem removed = accounts.remove(id);
        if (removed == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + id);
        }
        appendAudit("DELETE", removed.accountCode());
    }

    @GetMapping("/operations")
    public AccountOperationsResponse accountOperations() {
        List<AccountAuditEntry> audit = List.copyOf(auditEntries);
        List<AccountReconciliationEntry> reconciliations = accounts.values().stream()
                .sorted(Comparator.comparingLong(AccountItem::id))
                .map(a -> new AccountReconciliationEntry(
                        Instant.now(),
                        normalizeCode(a.accountCode()),
                        0,
                        0,
                        0,
                        0.0
                ))
                .toList();
        return new AccountOperationsResponse(audit, reconciliations, Instant.now());
    }

    private void appendAudit(String action, String accountCode) {
        auditEntries.add(0, new AccountAuditEntry(Instant.now(), "dashboard-ui", action, normalizeCode(accountCode)));
        if (auditEntries.size() > 200) {
            auditEntries.remove(auditEntries.size() - 1);
        }
    }

    private static String normalizeCode(String value) {
        return (value == null || value.isBlank()) ? "UNKNOWN" : value.trim();
    }

    private static String normalizeAccountType(String value) {
        return "PAPER".equalsIgnoreCase(value == null ? "" : value.trim()) ? "PAPER" : "EXECUTION";
    }

    public record AccountsResponse(List<AccountItem> items) {}

    public record AccountOperationsResponse(
            List<AccountAuditEntry> auditEntries,
            List<AccountReconciliationEntry> reconciliationEntries,
            Instant generatedAt
    ) {}

    public record AccountAuditEntry(
            Instant createdAt,
            String actor,
            String action,
            String accountCode
    ) {}

    public record AccountReconciliationEntry(
            Instant reconciledAt,
            String accountCode,
            int closedTrades,
            int matchedTrades,
            int unmatchedTrades,
            double totalClosedPnl
    ) {}

    public record AccountUpsertRequest(
            String accountType,
            String accountCode,
            Long accountNumber,
            String displayName,
            String host,
            Integer port,
            Integer timeoutMs,
            Boolean enabled,
            Integer priorityOrder,
            Double riskMultiplier,
            String reportEmails,
            String reportSubjectTemplate,
            String reportHtmlTemplate
    ) {}

    public record AccountItem(
            long id,
            String accountType,
            String accountCode,
            long accountNumber,
            String displayName,
            String host,
            int port,
            int timeoutMs,
            boolean enabled,
            int priorityOrder,
            double riskMultiplier,
            String reportEmails,
            String reportSubjectTemplate,
            String reportHtmlTemplate
    ) {
        private static long resolveAccountNumber(String accountType, Long requestedAccountNumber) {
            long requested = requestedAccountNumber == null ? 0L : requestedAccountNumber;
            if (requested > 0) {
                return requested;
            }
            if ("PAPER".equals(accountType)) {
                // Keep generated paper account numbers in a dedicated range.
                return 900_000_000L + (Instant.now().toEpochMilli() % 100_000_000L);
            }
            return 0L;
        }

        static AccountItem fromRequest(long id, AccountUpsertRequest request) {
            String accountType = normalizeAccountType(request.accountType());
            return new AccountItem(
                    id,
                    accountType,
                    normalizeCode(request.accountCode()),
                    resolveAccountNumber(accountType, request.accountNumber()),
                    request.displayName() == null ? "" : request.displayName().trim(),
                    request.host() == null || request.host().isBlank() ? "127.0.0.1" : request.host().trim(),
                    request.port() == null ? 5000 : request.port(),
                    request.timeoutMs() == null ? 5000 : request.timeoutMs(),
                    request.enabled() == null || request.enabled(),
                    request.priorityOrder() == null ? 100 : request.priorityOrder(),
                    request.riskMultiplier() == null ? 1.0 : request.riskMultiplier(),
                    request.reportEmails() == null ? "" : request.reportEmails().trim(),
                    request.reportSubjectTemplate() == null ? "" : request.reportSubjectTemplate(),
                    request.reportHtmlTemplate() == null ? "" : request.reportHtmlTemplate()
            );
        }

        AccountItem copy() {
            return new AccountItem(
                    id,
                    accountType,
                    accountCode,
                    accountNumber,
                    displayName,
                    host,
                    port,
                    timeoutMs,
                    enabled,
                    priorityOrder,
                    riskMultiplier,
                    reportEmails,
                    reportSubjectTemplate,
                    reportHtmlTemplate
            );
        }
    }
}



