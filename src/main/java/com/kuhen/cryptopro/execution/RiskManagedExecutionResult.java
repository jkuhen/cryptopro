package com.kuhen.cryptopro.execution;

import com.kuhen.cryptopro.risk.RiskManagementResult;

public record RiskManagedExecutionResult(
        RiskManagementResult risk,
        ExecutionResult execution
) {
}

