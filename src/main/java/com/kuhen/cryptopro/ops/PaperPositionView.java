package com.kuhen.cryptopro.ops;

public record PaperPositionView(
        String symbol,
        double quantity,
        double averagePrice,
        double markPrice,
        double unrealizedPnl
) {
}

