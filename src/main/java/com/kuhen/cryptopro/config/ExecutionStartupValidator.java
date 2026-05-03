package com.kuhen.cryptopro.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Validates cross-property execution configuration at startup.
 *
 * <p>Fail-fast rule: when execution is configured for Luno futures,
 * required Luno futures settings must be present and enabled.
 */
@Component
public class ExecutionStartupValidator implements InitializingBean {

    private final ExecutionProperties executionProperties;
    private final LunoProperties lunoProperties;

    public ExecutionStartupValidator(ExecutionProperties executionProperties, LunoProperties lunoProperties) {
        this.executionProperties = executionProperties;
        this.lunoProperties = lunoProperties;
    }

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    void validate() {
        String provider = normalize(executionProperties.getProvider());
        String marketType = normalize(executionProperties.getMarketType());

        if (!"LUNO".equals(provider) || !"FUTURES".equals(marketType)) {
            return;
        }

        List<String> issues = new ArrayList<>();

        if (!lunoProperties.isFuturesEnabled()) {
            issues.add("cryptopro.luno.futures-enabled must be true");
        }
        if (!hasText(lunoProperties.getFuturesBaseUrl())) {
            issues.add("cryptopro.luno.futures-base-url must not be blank");
        }
        if (!hasText(lunoProperties.getDefaultFuturesPair())) {
            issues.add("cryptopro.luno.default-futures-pair must not be blank");
        }
        if (!hasText(lunoProperties.getFuturesMarketOrderPath())) {
            issues.add("cryptopro.luno.futures-market-order-path must not be blank");
        }
        if (!hasText(lunoProperties.getFuturesLimitOrderPath())) {
            issues.add("cryptopro.luno.futures-limit-order-path must not be blank");
        }
        if (!hasText(lunoProperties.getFuturesOrderStatusPathTemplate())) {
            issues.add("cryptopro.luno.futures-order-status-path-template must not be blank");
        }

        Map<String, String> futuresSymbolMap = lunoProperties.getFuturesSymbolMap();
        if (futuresSymbolMap == null || futuresSymbolMap.isEmpty()) {
            issues.add("cryptopro.luno.futures-symbol-map must define at least one symbol mapping");
        }

        if (!issues.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid futures execution configuration for Luno (execution.provider=luno, execution.market-type=FUTURES): "
                            + String.join("; ", issues)
            );
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

