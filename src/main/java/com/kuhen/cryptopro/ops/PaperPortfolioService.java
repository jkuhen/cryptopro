package com.kuhen.cryptopro.ops;

import com.kuhen.cryptopro.config.PaperPortfolioProperties;
import com.kuhen.cryptopro.execution.ExecutionResult;
import com.kuhen.cryptopro.execution.ExecutionStatus;
import com.kuhen.cryptopro.strategy.SignalDirection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaperPortfolioService {

    private final PaperPortfolioProperties properties;

    private final Map<String, PositionState> positions = new HashMap<>();
    private final List<EquityPoint> equityCurve = new ArrayList<>();

    private double cashBalance;
    private double totalRealizedPnl;
    private double totalFees;

    /** Default constructor for framework instantiation. */
    public PaperPortfolioService() {
        this.properties = null;
    }

    @Autowired
    public PaperPortfolioService(PaperPortfolioProperties properties) {
        this.properties = properties;
        initializeState();
    }

    public synchronized PaperPortfolioSnapshot resetPortfolio() {
        initializeState();
        return snapshot(0.0, null);
    }

    public synchronized void applyExecution(String symbol, SignalDirection direction, ExecutionResult result, double markPrice) {
        if (result.status() != ExecutionStatus.FILLED && result.status() != ExecutionStatus.PARTIAL) {
            snapshot(markPrice, symbol);
            return;
        }

        double qty = result.filledQuantity();
        double price = result.averageFillPrice();
        if (qty <= 0.0 || price <= 0.0) {
            snapshot(markPrice, symbol);
            return;
        }

        double signedTradeQty = direction == SignalDirection.LONG ? qty : direction == SignalDirection.SHORT ? -qty : 0.0;
        if (signedTradeQty == 0.0) {
            snapshot(markPrice, symbol);
            return;
        }

        PositionState state = positions.computeIfAbsent(symbol, unused -> new PositionState());
        double existingQty = state.quantity;
        double existingAvg = state.averagePrice;

        double fee = Math.abs(qty * price) * properties.getFeeRate();
        totalFees += fee;

        cashBalance -= signedTradeQty * price;
        cashBalance -= fee;

        if (existingQty == 0.0 || Math.signum(existingQty) == Math.signum(signedTradeQty)) {
            double newQty = existingQty + signedTradeQty;
            double weightedNotional = (Math.abs(existingQty) * existingAvg) + (Math.abs(signedTradeQty) * price);
            state.quantity = newQty;
            state.averagePrice = Math.abs(newQty) < 1e-9 ? 0.0 : weightedNotional / Math.abs(newQty);
        } else {
            double closeQty = Math.min(Math.abs(existingQty), Math.abs(signedTradeQty));
            double realized = closeQty * (price - existingAvg) * Math.signum(existingQty);
            totalRealizedPnl += realized;

            double newQty = existingQty + signedTradeQty;
            if (Math.abs(newQty) < 1e-9) {
                state.quantity = 0.0;
                state.averagePrice = 0.0;
            } else if (Math.signum(newQty) == Math.signum(existingQty)) {
                state.quantity = newQty;
                // Keep old average when only partially reducing the existing position.
                state.averagePrice = existingAvg;
            } else {
                state.quantity = newQty;
                state.averagePrice = price;
            }
        }

        state.markPrice = markPrice > 0.0 ? markPrice : price;
        snapshot(markPrice > 0.0 ? markPrice : price, symbol);
    }

    public synchronized PaperPortfolioSnapshot snapshot(double fallbackMarkPrice, String symbolHint) {
        if (symbolHint != null && !symbolHint.isBlank()) {
            PositionState state = positions.get(symbolHint);
            if (state != null && fallbackMarkPrice > 0.0) {
                state.markPrice = fallbackMarkPrice;
            }
        }

        double unrealized = 0.0;
        double positionMarketValue = 0.0;
        List<PaperPositionView> views = new ArrayList<>();
        for (Map.Entry<String, PositionState> entry : positions.entrySet()) {
            String symbol = entry.getKey();
            PositionState state = entry.getValue();
            if (Math.abs(state.quantity) < 1e-9) {
                continue;
            }
            double mark = state.markPrice > 0.0 ? state.markPrice : state.averagePrice;
            double pnl = (mark - state.averagePrice) * state.quantity;
            unrealized += pnl;
            positionMarketValue += state.quantity * mark;
            views.add(new PaperPositionView(symbol, round(state.quantity), round(state.averagePrice), round(mark), round(pnl)));
        }

        double equity = cashBalance + positionMarketValue;
        equityCurve.add(new EquityPoint(Instant.now(), round(equity)));
        while (equityCurve.size() > properties.getMaxEquityPoints()) {
            equityCurve.remove(0);
        }

        return new PaperPortfolioSnapshot(
                round(cashBalance),
                round(equity),
                round(properties.getInitialCashUsd()),
                round(totalRealizedPnl),
                round(unrealized),
                round(totalFees),
                views,
                List.copyOf(equityCurve)
        );
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void initializeState() {
        positions.clear();
        equityCurve.clear();
        cashBalance = properties.getInitialCashUsd();
        totalRealizedPnl = 0.0;
        totalFees = 0.0;
        equityCurve.add(new EquityPoint(Instant.now(), round(cashBalance)));
    }

    private static class PositionState {
        private double quantity;
        private double averagePrice;
        private double markPrice;
    }
}


