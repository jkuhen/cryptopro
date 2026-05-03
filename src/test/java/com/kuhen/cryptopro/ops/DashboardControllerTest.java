package com.kuhen.cryptopro.ops;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void overviewReturnsKpiMonitoringAndAdministrationPayload() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.totalTransactions").exists())
                .andExpect(jsonPath("$.kpis.successRatePercent").exists())
                .andExpect(jsonPath("$.signalSummary.totalSignals").exists())
                .andExpect(jsonPath("$.signalSummary.winRateByCondition").isArray())
                .andExpect(jsonPath("$.portfolio.cashBalance").exists())
                .andExpect(jsonPath("$.portfolio.equity").exists())
                .andExpect(jsonPath("$.portfolio.positions").isArray())
                .andExpect(jsonPath("$.portfolio.equityCurve").isArray())
                .andExpect(jsonPath("$.administration.applicationName").isString())
                .andExpect(jsonPath("$.administration.aiModelVersion").isString())
                .andExpect(jsonPath("$.recentTransactions").isArray())
                .andExpect(jsonPath("$.recentErrors").isArray());
    }

    @Test
    void resetPortfolioEndpointReturnsFreshSnapshot() throws Exception {
        mockMvc.perform(post("/api/v1/dashboard/portfolio/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").exists())
                .andExpect(jsonPath("$.equity").exists())
                .andExpect(jsonPath("$.initialCash").exists())
                .andExpect(jsonPath("$.positions").isArray())
                .andExpect(jsonPath("$.equityCurve").isArray());
    }
}

