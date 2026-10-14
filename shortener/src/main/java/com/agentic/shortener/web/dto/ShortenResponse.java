package com.agentic.shortener.web.dto;

public record ShortenResponse(String code, String shortUrl, String originalUrl) {
}
