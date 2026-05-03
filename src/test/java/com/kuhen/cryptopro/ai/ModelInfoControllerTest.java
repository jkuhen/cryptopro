package com.kuhen.cryptopro.ai;

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
class ModelInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void modelInfoEndpointReturnsRuntimeRegistryData() throws Exception {
        mockMvc.perform(get("/api/v1/model/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").isString())
                .andExpect(jsonPath("$.modelName").isString())
                .andExpect(jsonPath("$.modelVersion").isString())
                .andExpect(jsonPath("$.artifactPath").isString())
                .andExpect(jsonPath("$.loaded").isBoolean())
                .andExpect(jsonPath("$.metadataMetrics").isMap())
                .andExpect(jsonPath("$.notes").isString());
    }
}

