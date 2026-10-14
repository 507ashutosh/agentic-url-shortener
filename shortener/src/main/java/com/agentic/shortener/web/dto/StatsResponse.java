package com.agentic.shortener.web.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

public record StatsResponse(
        @Schema(example = "Ab3dE9x") String code,
        @Schema(example = "https://example.com/some/long/path") String originalUrl,
        @Schema(description = "Number of redirects served", example = "3") long clickCount,
        Instant createdAt,
        @Schema(description = "Time of the last redirect; null if never visited") Instant lastAccessedAt) {
}
