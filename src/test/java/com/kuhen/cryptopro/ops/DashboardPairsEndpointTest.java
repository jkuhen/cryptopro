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

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
class DashboardPairsEndpointTest {

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
    void pairsEndpointReturnsBinanceUniverseInFuturesMode() throws Exception {
        when(binanceProperties.getDefaultSymbol()).thenReturn("BTCUSDT");
        when(binanceProperties.getWebsocketSymbols()).thenReturn(List.of("BTCUSDT", "ETHUSDT"));
        when(binanceProperties.getSymbolMap()).thenReturn(Map.of("SOLUSDT", "SOLUSDT"));
        when(executionProperties.isSpotMarket()).thenReturn(false);
        when(lunoProperties.getDefaultPair()).thenReturn("XRPUSDT");
        when(lunoProperties.getSymbolMap()).thenReturn(Map.of("ADAUSDT", "ADAUSDT"));

        mockMvc.perform(get("/api/v2/dashboard/pairs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairs").isArray())
                .andExpect(jsonPath("$.pairs[0]").value("BTCUSDT"))
                .andExpect(jsonPath("$.pairs[2]").value("SOLUSDT"));
    }

    @Test
    void pairsEndpointIncludesLunoSymbolsInSpotMode() throws Exception {
        when(binanceProperties.getDefaultSymbol()).thenReturn("BTCUSDT");
        when(binanceProperties.getWebsocketSymbols()).thenReturn(List.of("BTCUSDT", "ETHUSDT"));
        when(binanceProperties.getSymbolMap()).thenReturn(Map.of("SOLUSDT", "SOLUSDT"));
        when(executionProperties.isSpotMarket()).thenReturn(true);
        when(lunoProperties.getDefaultPair()).thenReturn("XRPUSDT");
        when(lunoProperties.getSymbolMap()).thenReturn(Map.of("ADAUSDT", "ADAUSDT"));

        mockMvc.perform(get("/api/v2/dashboard/pairs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairs").isArray())
                .andExpect(jsonPath("$.pairs[0]").value("ADAUSDT"))
                .andExpect(jsonPath("$.pairs[4]").value("XRPUSDT"));
    }
}


