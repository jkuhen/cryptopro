package com.kuhen.cryptopro.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuhen.cryptopro.backtest.BacktestRequest;
import com.kuhen.cryptopro.backtest.BacktestResult;
import com.kuhen.cryptopro.backtest.BacktestingService;
import com.kuhen.cryptopro.config.AutomationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(prefix = "cryptopro.automation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AutomationSchedulerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomationSchedulerService.class);
    private static final Pattern SYMBOL_FILE_PATTERN = Pattern.compile("Binance_(.+?)_d\\.csv", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter VERSION_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC);

    private final AutomationProperties automationProperties;
    private final BacktestingService backtestingService;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean backtestRunning = new AtomicBoolean(false);
    private final AtomicBoolean persistenceRunning = new AtomicBoolean(false);

    public AutomationSchedulerService(
            AutomationProperties automationProperties,
            BacktestingService backtestingService,
            ObjectMapper objectMapper
    ) {
        this.automationProperties = automationProperties;
        this.backtestingService = backtestingService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${cryptopro.automation.backtest.cron:0 0 */6 * * *}")
    public void scheduledBacktest() {
        if (!automationProperties.getBacktest().isEnabled()) {
            return;
        }
        if (!backtestRunning.compareAndSet(false, true)) {
            LOGGER.warn("Skipping automated backtest run because previous run is still in progress.");
            return;
        }

        try {
            runBacktestNow();
        } catch (Exception ex) {
            LOGGER.error("Automated backtest run failed: {}", ex.getMessage(), ex);
        } finally {
            backtestRunning.set(false);
        }
    }

    @Scheduled(cron = "${cryptopro.automation.persistence.cron:0 10 */6 * * *}")
    public void scheduledPersistence() {
        if (!automationProperties.getPersistence().isEnabled()) {
            return;
        }
        if (!persistenceRunning.compareAndSet(false, true)) {
            LOGGER.warn("Skipping automated history persistence because previous run is still in progress.");
            return;
        }

        try {
            runPersistenceNow();
        } catch (Exception ex) {
            LOGGER.error("Automated history persistence failed: {}", ex.getMessage(), ex);
        } finally {
            persistenceRunning.set(false);
        }
    }

    public synchronized void runBacktestNow() {
        AutomationProperties.Backtest cfg = automationProperties.getBacktest();
        Path inputDir = Path.of(cfg.getInputDir());
        List<Path> files = findHistoryFiles(inputDir, cfg.getFilePattern());
        if (files.isEmpty()) {
            LOGGER.warn("No history files found for automated backtest in {} pattern {}", inputDir.toAbsolutePath(), cfg.getFilePattern());
            return;
        }

        String version = VERSION_FMT.format(Instant.now());
        Path outputRoot = Path.of(cfg.getOutputDir());
        Path runDir = outputRoot.resolve(version);

        try {
            Files.createDirectories(runDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed creating automated backtest output directory " + runDir.toAbsolutePath(), ex);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Path file : files) {
            String symbol = resolveSymbol(file.getFileName().toString());
            BacktestRequest request = new BacktestRequest(
                    symbol,
                    file.toAbsolutePath().toString(),
                    cfg.getMaxCandles(),
                    cfg.getWarmupCandles(),
                    cfg.getAtrPeriod(),
                    cfg.getQuantity(),
                    cfg.getStopLossAtrMultiplier(),
                    cfg.getTakeProfitAtrMultiplier()
            );
            BacktestResult result = backtestingService.run(request);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("symbol", result.symbol());
            row.put("sourceFile", file.getFileName().toString());
            row.put("replayedCandles", result.replayedCandles());
            row.put("totalTrades", result.metrics().totalTrades());
            row.put("wins", result.metrics().wins());
            row.put("losses", result.metrics().losses());
            row.put("winRatePercent", result.metrics().winRatePercent());
            row.put("netProfit", result.metrics().netProfit());
            row.put("profitFactor", result.metrics().profitFactor());
            row.put("maxDrawdown", result.metrics().maxDrawdown());
            row.put("maxDrawdownPercent", result.metrics().maxDrawdownPercent());
            rows.add(row);
        }

        writeBacktestOutputs(outputRoot, runDir, version, rows);
        LOGGER.info("Automated backtest completed. version={} symbols={}", version, rows.size());
    }

    public synchronized void runPersistenceNow() {
        AutomationProperties.Persistence cfg = automationProperties.getPersistence();
        Path inputDir = Path.of(cfg.getInputDir());
        List<Path> files = findHistoryFiles(inputDir, cfg.getFilePattern());
        if (files.isEmpty()) {
            LOGGER.warn("No history files found for automated persistence in {} pattern {}", inputDir.toAbsolutePath(), cfg.getFilePattern());
            return;
        }

        String version = VERSION_FMT.format(Instant.now());
        Path outputRoot = Path.of(cfg.getOutputRoot());
        Path versionDir = outputRoot.resolve(version);
        Path analysisDir = versionDir.resolve("analysis");

        try {
            Files.createDirectories(analysisDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed creating automation artifact directory " + versionDir.toAbsolutePath(), ex);
        }

        List<Map<String, Object>> candles = new ArrayList<>();
        List<Map<String, Object>> features = new ArrayList<>();
        List<String> symbols = new ArrayList<>();

        for (Path file : files) {
            ParsedHistory parsed = parseHistory(file);
            if (parsed.rows().isEmpty()) {
                continue;
            }
            if (!symbols.contains(parsed.symbol())) {
                symbols.add(parsed.symbol());
            }
            candles.addAll(parsed.candleRows());
            features.addAll(parsed.featureRows());
        }

        Path candlesCsv = versionDir.resolve("history_candles_daily.csv");
        Path featuresCsv = versionDir.resolve("ai_features_daily.csv");
        Path datasetCard = versionDir.resolve("dataset_card.md");
        Path manifest = versionDir.resolve("manifest.json");

        writeCsv(candlesCsv, candles);
        writeCsv(featuresCsv, features);

        List<String> copied = copyAnalysisFiles(cfg.getAnalysisSourceDir(), analysisDir);
        int trainableRows = (int) features.stream().filter(row -> Boolean.parseBoolean(String.valueOf(row.get("is_trainable")))).count();

        List<String> card = List.of(
                "# History Artifact Dataset Card",
                "",
                "- version: " + version,
                "- generated_utc: " + Instant.now(),
                "- symbols: " + String.join(", ", symbols),
                "- candle_rows: " + candles.size(),
                "- feature_rows: " + features.size(),
                "- trainable_rows: " + trainableRows,
                "",
                "## Files",
                "",
                "- history_candles_daily.csv : normalized OHLCV daily candles",
                "- ai_features_daily.csv : engineered features + 1-day forward label",
                "- analysis/* : copied backtest outputs for auditability"
        );
        try {
            Files.write(datasetCard, card, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed writing dataset card " + datasetCard.toAbsolutePath(), ex);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", version);
        out.put("generatedAtUtc", Instant.now().toString());
        out.put("sourceFiles", files.stream().map(path -> path.getFileName().toString()).toList());
        out.put("symbols", symbols);
        out.put("candleRows", candles.size());
        out.put("featureRows", features.size());
        out.put("trainableRows", trainableRows);
        out.put("copiedAnalysis", copied);

        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifest.toFile(), out);
            Files.writeString(outputRoot.resolve("LATEST.txt"), version, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed writing automation manifest", ex);
        }

        LOGGER.info("Automated history persistence completed. version={} symbols={} candles={}", version, symbols.size(), candles.size());
    }

    private void writeBacktestOutputs(Path outputRoot, Path runDir, String version, List<Map<String, Object>> rows) {
        Path summaryCsv = runDir.resolve("backtest_summary.csv");
        Path summaryJson = runDir.resolve("backtest_summary.json");
        writeCsv(summaryCsv, rows);

        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(summaryJson.toFile(), rows);
            Files.writeString(outputRoot.resolve("LATEST.txt"), version, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed writing automated backtest output files", ex);
        }
    }

    private ParsedHistory parseHistory(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read history file " + file.toAbsolutePath(), ex);
        }

        if (lines.size() < 3) {
            return ParsedHistory.empty();
        }

        String[] header = splitCsv(lines.get(1));
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            idx.put(header[i].trim(), i);
        }

        int unixIdx = idx.getOrDefault("Unix", 0);
        int dateIdx = idx.getOrDefault("Date", 1);
        int symbolIdx = idx.getOrDefault("Symbol", 2);
        int openIdx = idx.getOrDefault("Open", 3);
        int highIdx = idx.getOrDefault("High", 4);
        int lowIdx = idx.getOrDefault("Low", 5);
        int closeIdx = idx.getOrDefault("Close", 6);
        int quoteVolumeIdx = idx.getOrDefault("Volume USDT", 8);
        int tradeCountIdx = idx.getOrDefault("tradecount", 9);

        String baseVolumeCol = header[7];
        for (String col : header) {
            if (col.startsWith("Volume ") && !"Volume USDT".equalsIgnoreCase(col)) {
                baseVolumeCol = col;
                break;
            }
        }
        int baseVolumeIdx = idx.getOrDefault(baseVolumeCol, 7);

        List<RawRow> rawRows = new ArrayList<>();
        for (int i = 2; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] cols = splitCsv(line);
            if (cols.length <= closeIdx) {
                continue;
            }
            rawRows.add(new RawRow(
                    safe(cols, unixIdx),
                    safe(cols, dateIdx),
                    safe(cols, symbolIdx),
                    parseDouble(safe(cols, openIdx)),
                    parseDouble(safe(cols, highIdx)),
                    parseDouble(safe(cols, lowIdx)),
                    parseDouble(safe(cols, closeIdx)),
                    parseDouble(safe(cols, baseVolumeIdx)),
                    parseDouble(safe(cols, quoteVolumeIdx)),
                    (long) parseDouble(safe(cols, tradeCountIdx))
            ));
        }

        rawRows.sort(Comparator.comparing(RawRow::date));
        if (rawRows.isEmpty()) {
            return ParsedHistory.empty();
        }

        String symbol = rawRows.get(0).symbol();
        int n = rawRows.size();
        double[] close = new double[n];
        double[] quoteVol = new double[n];
        for (int i = 0; i < n; i++) {
            close[i] = rawRows.get(i).close();
            quoteVol[i] = rawRows.get(i).volumeQuote();
        }

        double[] ret1 = new double[n];
        double[] ret3 = new double[n];
        double[] ret7 = new double[n];
        double[] fwd1 = new double[n];
        for (int i = 0; i < n; i++) {
            ret1[i] = i > 0 && close[i - 1] > 0.0 ? (close[i] / close[i - 1]) - 1.0 : 0.0;
            ret3[i] = i > 2 && close[i - 3] > 0.0 ? (close[i] / close[i - 3]) - 1.0 : 0.0;
            ret7[i] = i > 6 && close[i - 7] > 0.0 ? (close[i] / close[i - 7]) - 1.0 : 0.0;
            fwd1[i] = i < n - 1 && close[i] > 0.0 ? (close[i + 1] / close[i]) - 1.0 : 0.0;
        }

        double[] sma20 = sma(close, 20);
        double[] sma50 = sma(close, 50);
        double[] rsi14 = rsi(close, 14);
        double[] volStd14 = rollingStd(ret1, 14);
        double[] volSma20 = sma(quoteVol, 20);
        double[] volStd20 = rollingStd(quoteVol, 20);

        List<Map<String, Object>> candleRows = new ArrayList<>(n);
        List<Map<String, Object>> featureRows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            RawRow row = rawRows.get(i);
            Map<String, Object> candle = new LinkedHashMap<>();
            candle.put("symbol", row.symbol());
            candle.put("date", row.date());
            candle.put("unix_ms", row.unixMs());
            candle.put("open", row.open());
            candle.put("high", row.high());
            candle.put("low", row.low());
            candle.put("close", row.close());
            candle.put("volume_base", row.volumeBase());
            candle.put("volume_quote", row.volumeQuote());
            candle.put("trade_count", row.tradeCount());
            candle.put("source_file", file.getFileName().toString());
            candleRows.add(candle);

            double closeToSma20 = valid(sma20[i]) && sma20[i] != 0.0 ? (row.close() / sma20[i]) - 1.0 : 0.0;
            double closeToSma50 = valid(sma50[i]) && sma50[i] != 0.0 ? (row.close() / sma50[i]) - 1.0 : 0.0;
            double volumeZ20 = valid(volSma20[i]) && valid(volStd20[i]) && volStd20[i] > 0.0 ? (row.volumeQuote() - volSma20[i]) / volStd20[i] : 0.0;
            boolean trainable = i < n - 1;

            Map<String, Object> feature = new LinkedHashMap<>();
            feature.put("symbol", row.symbol());
            feature.put("date", row.date());
            feature.put("close", row.close());
            feature.put("ret_1d", ret1[i]);
            feature.put("ret_3d", ret3[i]);
            feature.put("ret_7d", ret7[i]);
            feature.put("volatility_14d", valid(volStd14[i]) ? volStd14[i] : 0.0);
            feature.put("rsi_14", valid(rsi14[i]) ? rsi14[i] : 50.0);
            feature.put("close_to_sma20", closeToSma20);
            feature.put("close_to_sma50", closeToSma50);
            feature.put("volume_z20", volumeZ20);
            feature.put("future_ret_1d", fwd1[i]);
            feature.put("label_up_1d", trainable && fwd1[i] > 0.0 ? 1 : 0);
            feature.put("is_trainable", trainable);
            featureRows.add(feature);
        }

        return new ParsedHistory(symbol, rawRows, candleRows, featureRows);
    }

    private List<String> copyAnalysisFiles(String sourceDir, Path targetDir) {
        List<String> copied = new ArrayList<>();
        Path source = Path.of(sourceDir);
        if (!Files.exists(source)) {
            return copied;
        }

        String[] names = new String[]{
                "backtest_metrics_by_symbol.csv",
                "backtest_summary.csv",
                "backtest_summary.json",
                "equity_curves.csv"
        };

        for (String name : names) {
            Path src = source.resolve(name);
            if (Files.exists(src)) {
                Path dst = targetDir.resolve(name);
                try {
                    Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    copied.add(name);
                } catch (IOException ex) {
                    LOGGER.warn("Failed to copy analysis file {}: {}", src.toAbsolutePath(), ex.getMessage());
                }
            }
        }
        return copied;
    }

    private void writeCsv(Path path, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            try {
                Files.writeString(path, "", StandardCharsets.UTF_8);
                return;
            } catch (IOException ex) {
                throw new IllegalStateException("Failed writing empty csv " + path.toAbsolutePath(), ex);
            }
        }

        List<String> headers = new ArrayList<>(rows.get(0).keySet());
        StringBuilder builder = new StringBuilder();
        builder.append(String.join(",", headers)).append('\n');
        for (Map<String, Object> row : rows) {
            List<String> values = new ArrayList<>(headers.size());
            for (String header : headers) {
                Object value = row.get(header);
                String txt = value == null ? "" : String.valueOf(value);
                if (txt.contains(",") || txt.contains("\"")) {
                    txt = '"' + txt.replace("\"", "\"\"") + '"';
                }
                values.add(txt);
            }
            builder.append(String.join(",", values)).append('\n');
        }

        try {
            Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed writing csv " + path.toAbsolutePath(), ex);
        }
    }

    private List<Path> findHistoryFiles(Path inputDir, String globPattern) {
        if (!Files.exists(inputDir)) {
            return List.of();
        }

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, globPattern)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    files.add(p);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed scanning history files in " + inputDir.toAbsolutePath(), ex);
        }

        files.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
        return files;
    }

    private String resolveSymbol(String fileName) {
        Matcher matcher = SYMBOL_FILE_PATTERN.matcher(fileName);
        if (matcher.matches()) {
            return matcher.group(1).toUpperCase(Locale.ROOT);
        }
        return "BTCUSDT";
    }

    private String[] splitCsv(String line) {
        return line.split(",");
    }

    private String safe(String[] values, int index) {
        if (index < 0 || index >= values.length) {
            return "";
        }
        return values[index].trim();
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private boolean valid(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private double[] sma(double[] series, int window) {
        double[] out = new double[series.length];
        double sum = 0.0;
        for (int i = 0; i < series.length; i++) {
            sum += series[i];
            if (i >= window) {
                sum -= series[i - window];
            }
            out[i] = i >= window - 1 ? sum / window : Double.NaN;
        }
        return out;
    }

    private double[] rollingStd(double[] series, int window) {
        double[] out = new double[series.length];
        for (int i = 0; i < series.length; i++) {
            if (i < window - 1) {
                out[i] = Double.NaN;
                continue;
            }
            int start = i - window + 1;
            double sum = 0.0;
            for (int j = start; j <= i; j++) {
                sum += series[j];
            }
            double mean = sum / window;
            double var = 0.0;
            for (int j = start; j <= i; j++) {
                double d = series[j] - mean;
                var += d * d;
            }
            out[i] = Math.sqrt(var / window);
        }
        return out;
    }

    private double[] rsi(double[] close, int period) {
        double[] out = new double[close.length];
        java.util.Arrays.fill(out, 50.0);
        if (close.length <= period) {
            return out;
        }

        double gain = 0.0;
        double loss = 0.0;
        for (int i = 1; i <= period; i++) {
            double delta = close[i] - close[i - 1];
            if (delta >= 0.0) {
                gain += delta;
            } else {
                loss += -delta;
            }
        }

        double avgGain = gain / period;
        double avgLoss = loss / period;
        out[period] = avgLoss == 0.0 ? 100.0 : 100.0 - (100.0 / (1.0 + (avgGain / avgLoss)));

        for (int i = period + 1; i < close.length; i++) {
            double delta = close[i] - close[i - 1];
            double up = delta > 0 ? delta : 0.0;
            double down = delta < 0 ? -delta : 0.0;
            avgGain = ((avgGain * (period - 1)) + up) / period;
            avgLoss = ((avgLoss * (period - 1)) + down) / period;
            out[i] = avgLoss == 0.0 ? 100.0 : 100.0 - (100.0 / (1.0 + (avgGain / avgLoss)));
        }
        return out;
    }

    private record RawRow(
            String unixMs,
            String date,
            String symbol,
            double open,
            double high,
            double low,
            double close,
            double volumeBase,
            double volumeQuote,
            long tradeCount
    ) {
    }

    private record ParsedHistory(
            String symbol,
            List<RawRow> rows,
            List<Map<String, Object>> candleRows,
            List<Map<String, Object>> featureRows
    ) {
        static ParsedHistory empty() {
            return new ParsedHistory("", List.of(), List.of(), List.of());
        }
    }
}

