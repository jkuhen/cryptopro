package com.kuhen.cryptopro.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PipelineDemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pipelineDemoEndpointReturnsFullSignalPayload() throws Exception {
        mockMvc.perform(get("/api/v1/pipeline/demo").param("symbol", "BTCUSDT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.biasScore").exists())
                .andExpect(jsonPath("$.smcScore").exists())
                .andExpect(jsonPath("$.derivativesScore").exists())
                .andExpect(jsonPath("$.finalScore").exists())
                .andExpect(jsonPath("$.riskPassed").exists())
                .andExpect(jsonPath("$.riskReasons").isArray())
                .andExpect(jsonPath("$.feedLatencyMs").exists())
                .andExpect(jsonPath("$.spreadBps").exists())
                .andExpect(jsonPath("$.aiProbabilityOfSuccess").exists())
                .andExpect(jsonPath("$.aiConfidence").exists())
                .andExpect(jsonPath("$.detectedRegime").isString())
                .andExpect(jsonPath("$.regimeConfidence").exists())
                .andExpect(jsonPath("$.aiModel").isString())
                .andExpect(jsonPath("$.aiModelVersion").isString())
                .andExpect(jsonPath("$.riskEngineAllowed").isBoolean())
                .andExpect(jsonPath("$.riskAtr").exists())
                .andExpect(jsonPath("$.riskStopLossPrice").exists())
                .andExpect(jsonPath("$.riskPerTradePercent").exists())
                .andExpect(jsonPath("$.riskDailyLossCapPercent").exists())
                .andExpect(jsonPath("$.riskConcurrentTrades").exists())
                .andExpect(jsonPath("$.executionStatus").isString())
                .andExpect(jsonPath("$.executionRequestedQuantity").exists())
                .andExpect(jsonPath("$.executionFilledQuantity").exists())
                .andExpect(jsonPath("$.executionLimitPrice").exists())
                .andExpect(jsonPath("$.executionSlippageBps").exists())
                .andExpect(jsonPath("$.executionSlices").isArray())
                .andExpect(jsonPath("$.rationale").isString());
    }
}

