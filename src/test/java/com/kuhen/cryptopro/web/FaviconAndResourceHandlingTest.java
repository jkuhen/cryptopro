package com.kuhen.cryptopro.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kuhen.cryptopro.ops.ApiExceptionHandler;
import com.kuhen.cryptopro.ops.OpsTelemetryService;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FaviconAndResourceHandlingTest.TestController.class)
@Import(ApiExceptionHandler.class)
class FaviconAndResourceHandlingTest {

    @RestController
    static class TestController {
        @GetMapping("/__test-ping")
        String ping() {
            return "ok";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpsTelemetryService opsTelemetryService;

    @Test
    void faviconIsServedFromStaticResources() throws Exception {
        MvcResult result = mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 0, "favicon should return non-empty content");
    }

    @Test
    void missingStaticResourceReturnsSpecific404Payload() throws Exception {
        mockMvc.perform(get("/missing-resource-test-file.css"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/missing-resource-test-file.css"))
                .andExpect(jsonPath("$.message").value("Resource not found"));
    }
}




