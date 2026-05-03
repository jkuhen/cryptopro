package com.kuhen.cryptopro.backtest;

import com.kuhen.cryptopro.ai.SignalScoringFeatures;
import com.kuhen.cryptopro.ai.SignalScoringModel;
import com.kuhen.cryptopro.ai.SignalScoringResult;
import com.kuhen.cryptopro.config.AiProperties;
import com.kuhen.cryptopro.data.FeatureEngineeringUtil;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.Timeframe;
import com.kuhen.cryptopro.strategy.SignalDirection;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class BacktestingService {

    private final SignalScoringModel signalScoringModel;
    private final AiProperties aiProperties;

    public BacktestingService(SignalScoringModel signalScoringModel, AiProperties aiProperties) {
        this.signalScoringModel = signalScoringModel;
        this.aiProperties = aiProperties;
    }

    public BacktestResult run(BacktestRequest request) {
        List<Candle> candles = replayHistoricalData(request.historicalCsvPath(), request.symbol(), request.maxCandles());
        if (candles.isEmpty()) {
            return new BacktestResult(request.symbol(), 0, emptyMetrics(), List.of(), List.of());
        }

        int warmup = Math.max(60, request.warmupCandles());
        double qty = request.quantity() <= 0.0 ? 1.0 : request.quantity();
        int atrPeriod = Math.max(5, request.atrPeriod());
        double stopMult = request.stopLossAtrMultiplier() <= 0.0 ? 2.0 : request.stopLossAtrMultiplier();
        double takeMult = request.takeProfitAtrMultiplier() <= 0.0 ? 3.0 : request.takeProfitAtrMultiplier();

        List<BacktestTrade> trades = new ArrayList<>();
        Position open = null;

        for (int i = warmup; i < candles.size(); i++) {
            Candle current = candles.get(i);
            List<Candle> history = candles.subList(0, i + 1);
            Indicators ind = computeIndicators(history, atrPeriod);
            SignalDecision decision = buildDecision(ind);

            if (open != null) {
                BacktestTrade exitTrade = tryExit(open, current, decision);
                if (exitTrade != null) {
                    trades.add(exitTrade);
                    open = null;
                }
            }

            if (open == null && decision.tradable() && decision.direction() != SignalDirection.NEUTRAL && ind.atr() > 0.0) {
                double entry = current.close();
                double stop = decision.direction() == SignalDirection.LONG
                        ? entry - ind.atr() * stopMult
                        : entry + ind.atr() * stopMult;
                double take = decision.direction() == SignalDirection.LONG
                        ? entry + ind.atr() * takeMult
                        : entry - ind.atr() * takeMult;
                open = new Position(decision.direction(), current.openTime(), entry, qty, stop, take);
            }
        }

        if (open != null) {
            Candle last = candles.get(candles.size() - 1);
            trades.add(closePosition(open, last.openTime(), last.close(), "END_OF_REPLAY"));
        }

        return new BacktestResult(request.symbol(), candles.size(), computeMetrics(trades), trades, candles);
    }

    public List<Candle> replayHistoricalData(String csvPath, String symbol, int maxCandles) {
        if (csvPath == null || csvPath.isBlank()) {
            return List.of();
        }

        List<Candle> rows = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(Path.of(csvPath), StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                if (line.startsWith("https://") || line.startsWith("Unix,")) {
                    continue;
                }

                String[] cols = line.split(",");
                if (cols.length < 8) {
                    continue;
                }

                try {
                    long ts = Long.parseLong(cols[0].trim());
                    String rowSymbol = cols[2].trim();
                    if (symbol != null && !symbol.isBlank() && !rowSymbol.equalsIgnoreCase(symbol.trim())) {
                        continue;
                    }

                    double open = Double.parseDouble(cols[3].trim());
                    double high = Double.parseDouble(cols[4].trim());
                    double low = Double.parseDouble(cols[5].trim());
                    double close = Double.parseDouble(cols[6].trim());
                    double volume = Double.parseDouble(cols[7].trim());

                    rows.add(new Candle(
                            rowSymbol,
                            Timeframe.H1,
                            Instant.ofEpochMilli(ts),
                            open,
                            high,
                            low,
                            close,
                            volume
                    ));
                } catch (Exception ignored) {
                    // Skip malformed rows to keep replay resilient to occasional bad lines.
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to replay historical data from " + csvPath, ex);
        }

        rows.sort(Comparator.comparing(Candle::openTime));
        if (maxCandles > 0 && rows.size() > maxCandles) {
            return new ArrayList<>(rows.subList(rows.size() - maxCandles, rows.size()));
        }
        return rows;
    }

    private SignalDecision buildDecision(Indicators indicators) {
        SignalDirection direction = strategyDirection(indicators);

        SignalScoringFeatures features = new SignalScoringFeatures(
                indicators.trendScore(),
                indicators.rsi(),
                indicators.volumeSpikeRatio(),
                0.0,
                0.0
        );
        SignalScoringResult ai = signalScoringModel.score(features);

        boolean aiPass = ai.probabilityOfSuccess() >= aiProperties.getThresholds().getMinProbability()
                && ai.confidence() >= aiProperties.getThresholds().getMinConfidence();

        return new SignalDecision(direction, aiPass, ai);
    }

    private SignalDirection strategyDirection(Indicators indicators) {
        double trendThreshold = 0.0015;
        if (Math.abs(indicators.trendScore()) < trendThreshold) {
            return SignalDirection.NEUTRAL;
        }
        if (indicators.trendScore() > 0 && indicators.rsi() < 70.0) {
            return SignalDirection.LONG;
        }
        if (indicators.trendScore() < 0 && indicators.rsi() > 30.0) {
            return SignalDirection.SHORT;
        }
        return SignalDirection.NEUTRAL;
    }

    private Indicators computeIndicators(List<Candle> history, int atrPeriod) {
        List<Double> closes = history.stream().map(Candle::close).toList();
        List<Double> highs = history.stream().map(Candle::high).toList();
        List<Double> lows = history.stream().map(Candle::low).toList();
        List<Double> volumes = history.stream().map(Candle::volume).toList();

        Double ema20 = FeatureEngineeringUtil.calculateEma(closes, 20);
        Double ema50 = FeatureEngineeringUtil.calculateEma(closes, 50);
        Double rsi = FeatureEngineeringUtil.calculateRsi(closes, 14);
        Double atr = FeatureEngineeringUtil.calculateAtrFromPrices(highs, lows, closes, atrPeriod);
        Double volumeMa = FeatureEngineeringUtil.calculateVolumeMA(volumes, 20);

        double lastVolume = volumes.get(volumes.size() - 1);
        double volumeSpike = (volumeMa == null || volumeMa <= 0.0) ? 1.0 : lastVolume / volumeMa;
        double trend = (ema20 == null || ema50 == null || ema50 == 0.0) ? 0.0 : (ema20 - ema50) / ema50;

        return new Indicators(
                trend,
                rsi == null ? 50.0 : rsi,
                atr == null ? 0.0 : atr,
                volumeSpike
        );
    }

    private BacktestTrade tryExit(Position open, Candle candle, SignalDecision decision) {
        if (open.direction() == SignalDirection.LONG) {
            if (candle.low() <= open.stopLoss()) {
                return closePosition(open, candle.openTime(), open.stopLoss(), "STOP_LOSS");
            }
            if (candle.high() >= open.takeProfit()) {
                return closePosition(open, candle.openTime(), open.takeProfit(), "TAKE_PROFIT");
            }
        } else {
            if (candle.high() >= open.stopLoss()) {
                return closePosition(open, candle.openTime(), open.stopLoss(), "STOP_LOSS");
            }
            if (candle.low() <= open.takeProfit()) {
                return closePosition(open, candle.openTime(), open.takeProfit(), "TAKE_PROFIT");
            }
        }

        if (decision.tradable() && decision.direction() != SignalDirection.NEUTRAL && decision.direction() != open.direction()) {
            return closePosition(open, candle.openTime(), candle.close(), "STRATEGY_REVERSAL");
        }
        return null;
    }

    private BacktestTrade closePosition(Position open, Instant time, double exitPrice, String reason) {
        double pnl = open.direction() == SignalDirection.LONG
                ? (exitPrice - open.entryPrice()) * open.quantity()
                : (open.entryPrice() - exitPrice) * open.quantity();

        return new BacktestTrade(
                open.direction(),
                open.entryTime(),
                time,
                open.entryPrice(),
                exitPrice,
                open.quantity(),
                pnl,
                reason
        );
    }

    private BacktestMetrics computeMetrics(List<BacktestTrade> trades) {
        if (trades.isEmpty()) {
            return emptyMetrics();
        }

        int wins = 0;
        int losses = 0;
        double grossProfit = 0.0;
        double grossLoss = 0.0;
        double net = 0.0;

        double equity = 0.0;
        double peak = 0.0;
        double maxDd = 0.0;
        double maxDdPct = 0.0;

        for (BacktestTrade t : trades) {
            if (t.pnl() > 0.0) {
                wins++;
                grossProfit += t.pnl();
            } else if (t.pnl() < 0.0) {
                losses++;
                grossLoss += t.pnl();
            }

            net += t.pnl();
            equity += t.pnl();
            if (equity > peak) {
                peak = equity;
            }

            double dd = peak - equity;
            if (dd > maxDd) {
                maxDd = dd;
                maxDdPct = peak > 0.0 ? (dd / peak) * 100.0 : 0.0;
            }
        }

        int total = trades.size();
        double winRate = (wins * 100.0) / total;
        double pf = grossLoss == 0.0 ? (grossProfit > 0.0 ? Double.MAX_VALUE : 0.0) : grossProfit / Math.abs(grossLoss);

        return new BacktestMetrics(
                total,
                wins,
                losses,
                round2(winRate),
                round2(grossProfit),
                round2(grossLoss),
                round2(net),
                round2(pf),
                round2(maxDd),
                round2(maxDdPct),
                round2(net / total)
        );
    }

    private BacktestMetrics emptyMetrics() {
        return new BacktestMetrics(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record Indicators(double trendScore, double rsi, double atr, double volumeSpikeRatio) {
    }

    private record SignalDecision(SignalDirection direction, boolean tradable, SignalScoringResult ai) {
    }

    private record Position(
            SignalDirection direction,
            Instant entryTime,
            double entryPrice,
            double quantity,
            double stopLoss,
            double takeProfit
    ) {
    }
}


