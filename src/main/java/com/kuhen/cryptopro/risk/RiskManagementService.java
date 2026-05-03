package com.kuhen.cryptopro.risk;

import com.kuhen.cryptopro.config.ExecutionProperties;
import com.kuhen.cryptopro.config.RiskProperties;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.strategy.SignalDirection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RiskManagementService {

    private static final double MAX_RISK_PER_TRADE_PERCENT = 1.0;
    private static final double MAX_DAILY_LOSS_CAP_PERCENT = 5.0;
    private static final int MAX_CONCURRENT_TRADES = 3;

    private final RiskProperties riskProperties;
    private final ExecutionProperties executionProperties;
    private final RiskStateService riskStateService;

    /** Default constructor for framework instantiation. */
    public RiskManagementService() {
        this.riskProperties = null;
        this.executionProperties = null;
        this.riskStateService = null;
    }

    @Autowired
    public RiskManagementService(
            RiskProperties riskProperties,
            ExecutionProperties executionProperties,
            RiskStateService riskStateService
    ) {
        this.riskProperties = riskProperties;
        this.executionProperties = executionProperties;
        this.riskStateService = riskStateService;
    }

    public RiskManagementResult evaluate(RiskManagementRequest request) {
        List<String> reasons = new ArrayList<>();
        double effectiveRiskPerTradePercent = Math.min(riskProperties.getRiskPerTradePercent(), MAX_RISK_PER_TRADE_PERCENT);
        double effectiveDailyLossCapPercent = Math.min(riskProperties.getDailyLossCapPercent(), MAX_DAILY_LOSS_CAP_PERCENT);
        int effectiveMaxConcurrentTrades = Math.min(riskProperties.getMaxConcurrentTrades(), MAX_CONCURRENT_TRADES);

        if (!request.tradable()) {
            reasons.add("Signal not tradable before risk management");
        }

        if (request.direction() == SignalDirection.NEUTRAL) {
            reasons.add("Neutral direction cannot be risk-managed for execution");
        }

        double dailyLoss = riskStateService.currentDailyLossPercent();
        if (dailyLoss >= effectiveDailyLossCapPercent) {
            reasons.add("Daily loss cap reached");
        }

        int concurrentTrades = riskStateService.currentConcurrentTrades();
        if (concurrentTrades >= effectiveMaxConcurrentTrades) {
            reasons.add("Max concurrent trades reached");
        }

        double atr = computeAtr(request.atrCandles(), riskProperties.getAtrPeriod());
        double stopDistance = atr * riskProperties.getAtrStopMultiplier();
        double stopLoss = deriveStopLoss(request.direction(), request.currentPrice(), stopDistance);

        double proposedQuantity = executionProperties.getBaseOrderQuantity() * request.proposedSizeMultiplier();
        double riskCapital = riskProperties.getAccountEquityUsd() * (effectiveRiskPerTradePercent / 100.0);
        double maxAllowedQuantity = stopDistance <= 0.0 ? proposedQuantity : (riskCapital / stopDistance);

        double adjustedSizeMultiplier = request.proposedSizeMultiplier();
        if (proposedQuantity > maxAllowedQuantity && executionProperties.getBaseOrderQuantity() > 0.0) {
            adjustedSizeMultiplier = Math.max(0.0, maxAllowedQuantity / executionProperties.getBaseOrderQuantity());
            reasons.add("Position size reduced to respect max risk per trade");
        }

        if (riskProperties.getRiskPerTradePercent() > MAX_RISK_PER_TRADE_PERCENT) {
            reasons.add("Configured risk per trade capped at 1%");
        }
        if (riskProperties.getDailyLossCapPercent() > MAX_DAILY_LOSS_CAP_PERCENT) {
            reasons.add("Configured daily loss cap capped at 5%");
        }
        if (riskProperties.getMaxConcurrentTrades() > MAX_CONCURRENT_TRADES) {
            reasons.add("Configured concurrent trade cap limited to 3");
        }

        boolean hardBlocked = reasons.stream().anyMatch(r -> r.contains("reached") || r.contains("Neutral") || r.contains("not tradable"));
        boolean allowed = !hardBlocked;

        return new RiskManagementResult(
                allowed,
                reasons,
                adjustedSizeMultiplier,
                atr,
                stopLoss,
                effectiveRiskPerTradePercent,
                dailyLoss,
                effectiveDailyLossCapPercent,
                concurrentTrades,
                effectiveMaxConcurrentTrades,
                maxAllowedQuantity
        );
    }

    public void recordExecution(com.kuhen.cryptopro.execution.ExecutionResult result) {
        riskStateService.recordExecution(result);
    }

    private double computeAtr(List<Candle> candles, int period) {
        if (candles == null || candles.size() < 2) {
            return 0.0;
        }

        int start = Math.max(1, candles.size() - period);
        double trSum = 0.0;
        int count = 0;

        for (int i = start; i < candles.size(); i++) {
            Candle current = candles.get(i);
            Candle previous = candles.get(i - 1);

            double tr1 = current.high() - current.low();
            double tr2 = Math.abs(current.high() - previous.close());
            double tr3 = Math.abs(current.low() - previous.close());
            trSum += Math.max(tr1, Math.max(tr2, tr3));
            count++;
        }

        return count == 0 ? 0.0 : trSum / count;
    }

    private double deriveStopLoss(SignalDirection direction, double currentPrice, double stopDistance) {
        if (currentPrice <= 0.0 || stopDistance <= 0.0) {
            return 0.0;
        }
        if (direction == SignalDirection.LONG) {
            return currentPrice - stopDistance;
        }
        if (direction == SignalDirection.SHORT) {
            return currentPrice + stopDistance;
        }
        return 0.0;
    }
}

