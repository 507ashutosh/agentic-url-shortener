package com.agentic.shortener.web.dto;

import java.time.Instant;

public record StatsResponse(String code, String originalUrl, long clickCount,
                            Instant createdAt, Instant lastAccessedAt) {
}
