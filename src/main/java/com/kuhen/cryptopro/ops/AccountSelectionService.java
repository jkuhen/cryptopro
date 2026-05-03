package com.kuhen.cryptopro.ops;

import com.kuhen.cryptopro.config.ExecutionProperties;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

@Service
public class AccountSelectionService {

    private final AccountsController accountsController;
    private final ExecutionProperties executionProperties;

    public AccountSelectionService(
            AccountsController accountsController,
            ExecutionProperties executionProperties
    ) {
        this.accountsController = accountsController;
        this.executionProperties = executionProperties;
    }

    public Optional<SelectedAccount> resolveActiveExecutionAccount() {
        String provider = normalizeProvider(executionProperties.getProvider());
        String requiredType = "paper".equals(provider) ? "PAPER" : "EXECUTION";

        return accountsController.listAccounts().items().stream()
                .filter(item -> item != null && item.enabled())
                .filter(item -> requiredType.equalsIgnoreCase(item.accountType()))
                .sorted(Comparator.comparingInt(AccountsController.AccountItem::priorityOrder)
                        .thenComparingLong(AccountsController.AccountItem::id))
                .findFirst()
                .map(item -> new SelectedAccount(
                        item.id(),
                        item.accountType(),
                        item.accountCode(),
                        item.accountNumber(),
                        provider,
                        item.host(),
                        item.port(),
                        item.timeoutMs(),
                        item.riskMultiplier()
                ));
    }

    private String normalizeProvider(String provider) {
        String normalized = String.valueOf(provider == null ? "paper" : provider).trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "paper" : normalized;
    }

    public record SelectedAccount(
            long id,
            String accountType,
            String accountCode,
            long accountNumber,
            String provider,
            String host,
            int port,
            int timeoutMs,
            double riskMultiplier
    ) {
    }
}

