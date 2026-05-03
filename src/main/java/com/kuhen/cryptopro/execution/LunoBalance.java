package com.kuhen.cryptopro.execution;

public record LunoBalance(
        String asset,
        double balance,
        double reserved,
        double unconfirmed
) {
}

