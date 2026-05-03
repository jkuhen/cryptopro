package com.kuhen.cryptopro.risk;

import com.kuhen.cryptopro.config.RiskProperties;
import com.kuhen.cryptopro.data.model.FeedStatus;
import com.kuhen.cryptopro.data.model.OrderBookSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RiskGateService {

    private final RiskProperties riskProperties;

    /** Default constructor for framework instantiation. */
    public RiskGateService() {
        this.riskProperties = null;
    }

    @Autowired
    public RiskGateService(RiskProperties riskProperties) {
        this.riskProperties = riskProperties;
    }

    public RiskGateResult evaluate(OrderBookSnapshot orderBook, FeedStatus feedStatus) {
        List<String> reasons = new ArrayList<>();

        double spreadBps = calculateSpreadBps(orderBook);
        if (spreadBps > riskProperties.getMaxSpreadBps()) {
            reasons.add("Spread above threshold");
        }

        if (feedStatus.latencyMs() > riskProperties.getMaxLatencyMs()) {
            reasons.add("Feed latency above threshold");
        }

        if (feedStatus.feedAgeSeconds() > riskProperties.getStaleFeedSeconds()) {
            reasons.add("Market data feed is stale");
        }

        return new RiskGateResult(reasons.isEmpty(), reasons, spreadBps, feedStatus.latencyMs(), feedStatus.feedAgeSeconds());
    }

    private double calculateSpreadBps(OrderBookSnapshot orderBook) {
        if (orderBook.bids().isEmpty() || orderBook.asks().isEmpty()) {
            return Double.MAX_VALUE;
        }

        double bid = orderBook.bids().get(0).price();
        double ask = orderBook.asks().get(0).price();
        double mid = (bid + ask) / 2.0;
        if (mid <= 0.0) {
            return Double.MAX_VALUE;
        }
        return ((ask - bid) / mid) * 10_000.0;
    }
}

