package com.agentic.shortener;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "shortener.rate-limit.requests=2")
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void thirdShortenRequestInTheWindowGets429ButReadsStillWork() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/shorten").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\": \"https://example.com\"}"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/shorten").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value(429));

        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
