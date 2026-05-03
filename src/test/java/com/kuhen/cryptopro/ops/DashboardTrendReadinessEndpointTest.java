package com.kuhen.cryptopro.ops;

import com.kuhen.cryptopro.ai.ModelInfoProvider;
import com.kuhen.cryptopro.config.AiProperties;
import com.kuhen.cryptopro.config.BinanceProperties;
import com.kuhen.cryptopro.config.ExecutionProperties;
import com.kuhen.cryptopro.config.LunoProperties;
import com.kuhen.cryptopro.data.MarketDataProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
class DashboardTrendReadinessEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpsTelemetryService opsTelemetryService;
    @MockitoBean
    private ModelInfoProvider modelInfoProvider;
    @MockitoBean
    private ExecutionProperties executionProperties;
    @MockitoBean
    private AiProperties aiProperties;
    @MockitoBean
    private SignalTelemetryService signalTelemetryService;
    @MockitoBean
    private SignalOversightService signalOversightService;
    @MockitoBean
    private TransactionReportService transactionReportService;
    @MockitoBean
    private TrendReadinessService trendReadinessService;
    @MockitoBean
    private PaperPortfolioService paperPortfolioService;
    @MockitoBean
    private MarketDataProvider marketDataProvider;
    @MockitoBean
    private BinanceProperties binanceProperties;
    @MockitoBean
    private LunoProperties lunoProperties;

    @Test
    void trendReadinessEndpointReturnsRows() throws Exception {
        TrendReadinessResponse response = new TrendReadinessResponse(
                Instant.parse("2026-04-27T09:00:00Z"),
                48,
                List.of(new TrendReadinessRow(
                        "BTCUSDT",
                        48,
                        48,
                        100.0,
                        48,
                        Instant.parse("2026-04-15T00:00:00Z"),
                        "READY"
                ))
        );

        when(binanceProperties.getDefaultSymbol()).thenReturn("BTCUSDT");
        when(binanceProperties.getWebsocketSymbols()).thenReturn(List.of("ETHUSDT"));
        when(binanceProperties.getSymbolMap()).thenReturn(Map.of("SOLUSDT", "SOLUSDT"));
        when(trendReadinessService.buildReport(eq(48), anyCollection()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v2/dashboard/admin/trend-readiness").param("hours", "48"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours").value(48))
                .andExpect(jsonPath("$.rows[0].symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.rows[0].status").value("READY"));

        verify(trendReadinessService).buildReport(eq(48), argThat((Collection<String> symbols) ->
                symbols != null && symbols.size() == 3
                        && symbols.contains("BTCUSDT")
                        && symbols.contains("ETHUSDT")
                        && symbols.contains("SOLUSDT")
        ));
    }
}


