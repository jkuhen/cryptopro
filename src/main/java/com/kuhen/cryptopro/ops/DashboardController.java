package com.kuhen.cryptopro.ops;

import com.kuhen.cryptopro.ai.ModelInfoProvider;
import com.kuhen.cryptopro.ai.ModelInfoResponse;
import com.kuhen.cryptopro.config.AiProperties;
import com.kuhen.cryptopro.config.BinanceProperties;
import com.kuhen.cryptopro.config.ExecutionProperties;
import com.kuhen.cryptopro.config.LunoProperties;
import com.kuhen.cryptopro.data.MarketDataProvider;
import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.Timeframe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

@RestController
@RequestMapping({"/api/v1/dashboard", "/api/v2/dashboard"})
public class DashboardController {

    private final OpsTelemetryService opsTelemetryService;
    private final ModelInfoProvider modelInfoProvider;
    private final Environment environment;
    private final ExecutionProperties executionProperties;
    private final AiProperties aiProperties;
    private final SignalTelemetryService signalTelemetryService;
    private final SignalOversightService signalOversightService;
    private final TransactionReportService transactionReportService;
    private final TrendReadinessService trendReadinessService;
    private final PaperPortfolioService paperPortfolioService;
    private final MarketDataProvider marketDataProvider;
    private final BinanceProperties binanceProperties;
    private final LunoProperties lunoProperties;

    @Value("${spring.application.name:cryptopro}")
    private String applicationName;

    public DashboardController(
            OpsTelemetryService opsTelemetryService,
            ModelInfoProvider modelInfoProvider,
            Environment environment,
            ExecutionProperties executionProperties,
            AiProperties aiProperties,
            SignalTelemetryService signalTelemetryService,
            SignalOversightService signalOversightService,
            TransactionReportService transactionReportService,
            TrendReadinessService trendReadinessService,
            PaperPortfolioService paperPortfolioService,
            MarketDataProvider marketDataProvider,
            BinanceProperties binanceProperties,
            LunoProperties lunoProperties
    ) {
        this.opsTelemetryService = opsTelemetryService;
        this.modelInfoProvider = modelInfoProvider;
        this.environment = environment;
        this.executionProperties = executionProperties;
        this.aiProperties = aiProperties;
        this.signalTelemetryService = signalTelemetryService;
        this.signalOversightService = signalOversightService;
        this.transactionReportService = transactionReportService;
        this.trendReadinessService = trendReadinessService;
        this.paperPortfolioService = paperPortfolioService;
        this.marketDataProvider = marketDataProvider;
        this.binanceProperties = binanceProperties;
        this.lunoProperties = lunoProperties;
    }

    @GetMapping("/pairs")
    public Map<String, List<String>> pairs() {
        TreeSet<String> values = new TreeSet<>();
        values.add(binanceProperties.getDefaultSymbol());
        values.addAll(binanceProperties.getWebsocketSymbols());
        values.addAll(binanceProperties.getSymbolMap().keySet());
        if (executionProperties.isSpotMarket()) {
            values.add(lunoProperties.getDefaultPair());
            values.addAll(lunoProperties.getSymbolMap().keySet());
        }
        values.removeIf(s -> s == null || s.isBlank());
        return Map.of("pairs", List.copyOf(values));
    }

    @GetMapping("/overview")
    public DashboardOverviewResponse overview() {
        ModelInfoResponse modelInfo = modelInfoProvider.currentModelInfo();
        AdministrationInfo admin = new AdministrationInfo(
                applicationName,
                Arrays.asList(environment.getActiveProfiles()),
                environment.getProperty("cryptopro.data.provider", "unknown"),
                aiProperties.getModel().getProvider(),
                modelInfo.modelVersion(),
                executionProperties.getProvider(),
                executionProperties.getMarketType(),
                executionProperties.isEnabled(),
                opsTelemetryService.uptimeSeconds(),
                environment.getProperty("server.port", "8080")
        );

        return new DashboardOverviewResponse(
                opsTelemetryService.buildKpis(),
                signalTelemetryService.buildSummary(100),
                paperPortfolioService.snapshot(0.0, null),
                admin,
                opsTelemetryService.recentTransactions(25),
                opsTelemetryService.recentErrors(25)
        );
    }

    @PostMapping("/portfolio/reset")
    public PaperPortfolioSnapshot resetPortfolio() {
        return paperPortfolioService.resetPortfolio();
    }

    @GetMapping("/latest-candles")
    public List<Candle> latestCandles(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "M1") String timeframe,
            @RequestParam(defaultValue = "12") int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        Timeframe tf;
        try {
            tf = Timeframe.valueOf(timeframe.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            tf = Timeframe.M1;
        }

        List<Candle> candles = marketDataProvider.getRecentCandles(symbol, tf, safeLimit);
        if (candles.size() <= safeLimit) {
            return candles;
        }
        return candles.subList(candles.size() - safeLimit, candles.size());
    }

    /**
     * Signal oversight report — powers the Open Signals and Missed Trades cards.
     *
     * <p>Pass {@code symbol=ALL} (or omit / leave blank) to include all symbols.
     */
    @GetMapping("/reports/signals")
    public SignalOversightResponse signalOversight(
            @RequestParam(defaultValue = "ALL") String symbol,
            @RequestParam(defaultValue = "2") int days,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "60") int staleMinutes,
            @RequestParam(defaultValue = "") String timeframe,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String reason
    ) {
        int safeDays = Math.max(1, Math.min(days, 90));
        int safeLimit = Math.max(1, Math.min(limit, 500));
        int safeStale = Math.max(1, Math.min(staleMinutes, 1440));
        return signalOversightService.buildOversightReport(symbol, safeDays, safeLimit, safeStale);
    }

    @GetMapping("/reports/transactions")
    public TransactionReportResponse transactionReport(
            @RequestParam(defaultValue = "ALL") String symbol,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "") String accountId
    ) {
        int safeDays = Math.max(1, Math.min(days, 90));
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        return transactionReportService.buildReport(symbol, safeDays, safeLimit, accountId);
    }

    @GetMapping("/admin/trend-readiness")
    public TrendReadinessResponse trendReadiness(
            @RequestParam(defaultValue = "48") int hours
    ) {
        TreeSet<String> symbols = new TreeSet<>();
        symbols.add(binanceProperties.getDefaultSymbol());
        symbols.addAll(binanceProperties.getWebsocketSymbols());
        symbols.addAll(binanceProperties.getSymbolMap().keySet());
        symbols.removeIf(s -> s == null || s.isBlank());
        return trendReadinessService.buildReport(hours, symbols);
    }
}
