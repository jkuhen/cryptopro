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
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
class DashboardTransactionsEndpointTest {

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
    void transactionsReportEndpointReturnsExpectedPayload() throws Exception {
        TransactionReportResponse response = new TransactionReportResponse(
                Instant.parse("2026-04-27T07:00:00Z"),
                3,
                2,
                1,
                100.0,
                123.45,
                150.00,
                -26.55,
                123.45,
                List.of(new TransactionReportRow(
                        Instant.parse("2026-04-27T06:45:00Z"),
                        Instant.parse("2026-04-27T06:30:00Z"),
                        "BTCUSDT",
                        "BUY",
                        65000.0,
                        1.2,
                        "WIN",
                        123.45,
                        "PAPER",
                        "paper",
                        "TRADE_DB"
                ))
        );

        when(transactionReportService.buildReport(eq("ALL"), eq(7), eq(100), eq(""))).thenReturn(response);

        mockMvc.perform(get("/api/v2/dashboard/reports/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrades").value(3))
                .andExpect(jsonPath("$.pnlPeriod").value(123.45))
                .andExpect(jsonPath("$.rows[0].symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.rows[0].outcome").value("WIN"));

        verify(transactionReportService).buildReport("ALL", 7, 100, "");
    }
}

