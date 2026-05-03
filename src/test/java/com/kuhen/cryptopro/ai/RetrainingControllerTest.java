package com.kuhen.cryptopro.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RetrainingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void retrainingEndpointsReturnStatusShape() throws Exception {
        mockMvc.perform(get("/api/v1/retraining/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").isBoolean())
                .andExpect(jsonPath("$.totalRuns").exists())
                .andExpect(jsonPath("$.lastRunSuccessful").isBoolean())
                .andExpect(jsonPath("$.message").isString());

        mockMvc.perform(post("/api/v1/retraining/run")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").isBoolean())
                .andExpect(jsonPath("$.samplesUsed").exists())
                .andExpect(jsonPath("$.message").isString());
    }
}

