package com.kuhen.cryptopro.ops;

import java.util.List;

public record AdministrationInfo(
        String applicationName,
        List<String> activeProfiles,
        String dataProvider,
        String aiModelProvider,
        String aiModelVersion,
        String executionProvider,
        String executionMarketType,
        boolean executionEnabled,
        long uptimeSeconds,
        String serverPort
) {
}

