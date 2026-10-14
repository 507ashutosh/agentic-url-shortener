package com.agentic.shortener.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under the {@code shortener.*} prefix in application.properties. */
@ConfigurationProperties(prefix = "shortener")
public record ShortenerProperties(String baseUrl, RateLimit rateLimit) {

    public record RateLimit(int requests, Duration window) {
    }
}
