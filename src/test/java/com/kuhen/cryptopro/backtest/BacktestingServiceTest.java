package com.kuhen.cryptopro.backtest;

import com.kuhen.cryptopro.ai.SignalScoringModel;
import com.kuhen.cryptopro.ai.SignalScoringResult;
import com.kuhen.cryptopro.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BacktestingServiceTest {

    @Test
    void replaysHistoricalDataAndProducesPerformanceMetrics() throws Exception {
        Path csv = Files.createTempFile("backtest-", ".csv");
        Files.writeString(csv, buildCsv(), StandardCharsets.UTF_8);

        SignalScoringModel scoringModel = mock(SignalScoringModel.class);
        when(scoringModel.score(any())).thenReturn(new SignalScoringResult(0.8, 0.8, "mock", "v1", "ok"));

        AiProperties aiProperties = new AiProperties();
        aiProperties.getThresholds().setMinProbability(0.55);
        aiProperties.getThresholds().setMinConfidence(0.25);

        BacktestingService service = new BacktestingService(scoringModel, aiProperties);

        BacktestResult result = service.run(new BacktestRequest(
                "BTCUSDT",
                csv.toString(),
                0,
                60,
                14,
                1.0,
                2.0,
                3.0
        ));

        assertTrue(result.replayedCandles() > 100);
        assertFalse(result.trades().isEmpty());
        assertTrue(result.metrics().totalTrades() > 0);
        assertEquals(result.metrics().totalTrades(), result.trades().size());
        assertTrue(result.metrics().winRatePercent() >= 0.0 && result.metrics().winRatePercent() <= 100.0);
    }

    @Test
    void returnsEmptyMetricsWhenCsvIsEmpty() throws Exception {
        Path csv = Files.createTempFile("backtest-empty-", ".csv");
        Files.writeString(csv, "https://www.CryptoDataDownload.com\nUnix,Date,Symbol,Open,High,Low,Close,Volume BTC,Volume USDT,tradecount\n", StandardCharsets.UTF_8);

        SignalScoringModel scoringModel = mock(SignalScoringModel.class);
        when(scoringModel.score(any())).thenReturn(new SignalScoringResult(0.8, 0.8, "mock", "v1", "ok"));

        BacktestingService service = new BacktestingService(scoringModel, new AiProperties());
        BacktestResult result = service.run(BacktestRequest.defaults("BTCUSDT", csv.toString()));

        assertEquals(0, result.replayedCandles());
        assertEquals(0, result.metrics().totalTrades());
    }

    private String buildCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("https://www.CryptoDataDownload.com\n");
        sb.append("Unix,Date,Symbol,Open,High,Low,Close,Volume BTC,Volume USDT,tradecount\n");

        // Build oldest-to-newest then reverse to match CryptoDataDownload format (newest first).
        List<String> rows = new ArrayList<>();
        double px = 10000.0;
        for (int i = 0; i < 220; i++) {
            if (i < 110) {
                px += 35.0;
            } else {
                px -= 30.0;
            }
            double open = px - 5.0;
            double high = px + 120.0;
            double low = px - 120.0;
            double close = px;
            double volume = 100.0 + (i % 15) * 8.0;

            LocalDate date = LocalDate.of(2025, 1, 1).plusDays(i);
            long ts = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            rows.add(ts + "," + date + ",BTCUSDT," + open + "," + high + "," + low + "," + close + "," + volume + ",1000000,1000");
        }

        for (int i = rows.size() - 1; i >= 0; i--) {
            sb.append(rows.get(i)).append('\n');
        }
        return sb.toString();
    }
}


