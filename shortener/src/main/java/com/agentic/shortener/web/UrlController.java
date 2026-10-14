package com.agentic.shortener.web;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.agentic.shortener.config.ShortenerProperties;
import com.agentic.shortener.domain.UrlMapping;
import com.agentic.shortener.service.UrlService;
import com.agentic.shortener.web.dto.ShortenRequest;
import com.agentic.shortener.web.dto.ShortenResponse;
import com.agentic.shortener.web.dto.StatsResponse;

@RestController
public class UrlController {

    // Codes are letters and digits only, so /{code} never swallows other paths.
    private static final String CODE = "{code:[A-Za-z0-9]{4,10}}";

    private final UrlService urlService;
    private final ShortenerProperties properties;

    public UrlController(UrlService urlService, ShortenerProperties properties) {
        this.urlService = urlService;
        this.properties = properties;
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(@RequestBody ShortenRequest request) {
        UrlMapping mapping = urlService.shorten(request.url());
        String shortUrl = properties.baseUrl() + "/" + mapping.getCode();
        return ResponseEntity.created(URI.create(shortUrl))
                .body(new ShortenResponse(mapping.getCode(), shortUrl, mapping.getOriginalUrl()));
    }

    // 302 (temporary) rather than 301: browsers cache 301s, which would hide later clicks from the stats.
    @GetMapping("/" + CODE)
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(urlService.resolve(code)))
                .build();
    }

    @GetMapping("/stats/" + CODE)
    public StatsResponse stats(@PathVariable String code) {
        UrlMapping mapping = urlService.stats(code);
        return new StatsResponse(mapping.getCode(), mapping.getOriginalUrl(), mapping.getClickCount(),
                mapping.getCreatedAt(), mapping.getLastAccessedAt());
    }
}
