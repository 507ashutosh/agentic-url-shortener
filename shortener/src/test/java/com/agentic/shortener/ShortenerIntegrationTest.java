package com.agentic.shortener;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.jayway.jsonpath.JsonPath;

/** Starts the whole application with a real H2 database and calls it over HTTP. */
@SpringBootTest(properties = "shortener.rate-limit.requests=1000")
@AutoConfigureMockMvc
class ShortenerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shortenThenRedirectThenStats() throws Exception {
        String code = shorten("https://example.com/some/long/path");

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/some/long/path"));
        mockMvc.perform(get("/" + code)).andExpect(status().isFound());

        mockMvc.perform(get("/stats/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/some/long/path"))
                .andExpect(jsonPath("$.clickCount").value(2))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.lastAccessedAt").isNotEmpty());
    }

    @Test
    void shortenReturnsTheShortUrlAndLocationHeader() throws Exception {
        mockMvc.perform(post("/shorten").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortUrl").value(org.hamcrest.Matchers.startsWith("http://localhost:8080/")))
                .andExpect(header().exists("Location"));
    }

    @Test
    void newLinkStartsWithZeroClicks() throws Exception {
        String code = shorten("https://example.com");

        mockMvc.perform(get("/stats/" + code))
                .andExpect(jsonPath("$.clickCount").value(0))
                .andExpect(jsonPath("$.lastAccessedAt").isEmpty());
    }

    @Test
    void invalidUrlIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/shorten").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("url must start with http:// or https://"));
    }

    @Test
    void missingOrMalformedBodyIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/shorten").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/shorten").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownCodeReturns404ForRedirectAndStats() throws Exception {
        mockMvc.perform(get("/nosuch1")).andExpect(status().isNotFound());
        mockMvc.perform(get("/stats/nosuch1")).andExpect(status().isNotFound());
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private String shorten(String url) throws Exception {
        String body = mockMvc.perform(post("/shorten").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"" + url + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.code");
        assertThat(code).hasSize(7);
        return code;
    }
}
