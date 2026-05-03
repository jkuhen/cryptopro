package com.kuhen.cryptopro.ops;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountsController.class)
class AccountsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpsTelemetryService opsTelemetryService;

    @Test
    void supportsCrudAndOperationsEndpoints() throws Exception {
        String createBody = """
                {
                  "accountType": "EXECUTION",
                  "accountCode": "ACC_EU_01",
                  "accountNumber": 123456,
                  "displayName": "EU Main",
                  "host": "127.0.0.1",
                  "port": 5000,
                  "timeoutMs": 5000,
                  "enabled": true,
                  "priorityOrder": 100,
                  "riskMultiplier": 1.0,
                  "reportEmails": "ops@example.com"
                }
                """;

        mockMvc.perform(post("/api/v2/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.accountType").value("EXECUTION"))
                .andExpect(jsonPath("$.accountCode").value("ACC_EU_01"));

        mockMvc.perform(get("/api/v2/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].accountCode").value("ACC_EU_01"));

        String updateBody = """
                {
                  "accountType": "PAPER",
                  "accountCode": "ACC_EU_01",
                  "accountNumber": 123456,
                  "displayName": "EU Main Updated",
                  "host": "127.0.0.1",
                  "port": 5001,
                  "timeoutMs": 6000,
                  "enabled": true,
                  "priorityOrder": 90,
                  "riskMultiplier": 1.1,
                  "reportEmails": "ops@example.com"
                }
                """;

        mockMvc.perform(put("/api/v2/accounts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("PAPER"))
                .andExpect(jsonPath("$.displayName").value("EU Main Updated"))
                .andExpect(jsonPath("$.port").value(5001));

        mockMvc.perform(get("/api/v2/accounts/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditEntries").isArray())
                .andExpect(jsonPath("$.reconciliationEntries").isArray())
                .andExpect(jsonPath("$.generatedAt").isString());

        mockMvc.perform(delete("/api/v2/accounts/1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v2/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }
}


