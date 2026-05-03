package com.kuhen.cryptopro.execution;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExecutionSimulationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void simulationEndpointAcceptsRuntimeOverrides() throws Exception {
        String body = """
                {
                  "symbol": "BTCUSDT",
                  "direction": "LONG",
                  "tradable": true,
                  "quantity": 1.5,
                  "maxPartialEntries": 3,
                  "slippageToleranceBps": 8.0,
                  "limitOffsetBps": 2.0,
                  "bids": [{"price": 64999.0, "size": 2.0}],
                  "asks": [{"price": 65001.0, "size": 2.0}]
                }
                """;

        mockMvc.perform(post("/api/v1/execution/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.requestedQuantity").value(1.5))
                .andExpect(jsonPath("$.partialEntries").value(3))
                .andExpect(jsonPath("$.limitPrice").exists())
                .andExpect(jsonPath("$.slippageBps").exists())
                .andExpect(jsonPath("$.slices").isArray())
                .andExpect(jsonPath("$.notes").isString());
    }
}

