package com.agentic.shortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ShortenRequest(
        @Schema(description = "The http(s) URL to shorten, at most 2048 characters",
                example = "https://example.com/some/long/path", requiredMode = Schema.RequiredMode.REQUIRED)
        String url) {
}
