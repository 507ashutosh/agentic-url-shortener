package com.agentic.shortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ShortenResponse(
        @Schema(example = "Ab3dE9x") String code,
        @Schema(example = "http://localhost:8080/Ab3dE9x") String shortUrl,
        @Schema(example = "https://example.com/some/long/path") String originalUrl) {
}
