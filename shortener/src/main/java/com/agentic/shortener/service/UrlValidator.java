package com.agentic.shortener.service;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.stereotype.Component;

/** Decides whether a submitted URL is acceptable to shorten. */
@Component
public class UrlValidator {

    static final int MAX_LENGTH = 2048;

    /** Returns the trimmed URL, or throws {@link InvalidUrlException} saying what is wrong. */
    public String validate(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidUrlException("url is required");
        }
        String trimmed = url.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidUrlException("url must be at most " + MAX_LENGTH + " characters");
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("url is not a valid URL");
        }
        // Only web links: blocks javascript:, file:, data: and similar schemes.
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidUrlException("url must start with http:// or https://");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("url must contain a host");
        }
        return trimmed;
    }
}
