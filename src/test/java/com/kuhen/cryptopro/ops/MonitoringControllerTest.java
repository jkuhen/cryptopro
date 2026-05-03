package com.kuhen.cryptopro.ops;

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
class MonitoringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void monitoringEndpointsReturnLogsArrays() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/transactions").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/v1/monitoring/errors").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}

