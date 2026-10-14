package com.agentic.shortener.web;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Short links")
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
    @Operation(summary = "Create a short link")
    @ApiResponse(responseCode = "201", description = "Link created; Location holds the short URL")
    @ApiResponse(responseCode = "400", description = "Missing, malformed or non-http(s) URL",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Too many links created from this IP; see Retry-After",
            headers = @Header(name = "Retry-After", description = "Seconds to wait"),
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ShortenResponse> shorten(@RequestBody ShortenRequest request) {
        UrlMapping mapping = urlService.shorten(request.url());
        String shortUrl = properties.baseUrl() + "/" + mapping.getCode();
        return ResponseEntity.created(URI.create(shortUrl))
                .body(new ShortenResponse(mapping.getCode(), shortUrl, mapping.getOriginalUrl()));
    }

    // 302 (temporary) rather than 301: browsers cache 301s, which would hide later clicks from the stats.
    @GetMapping("/" + CODE)
    @Operation(summary = "Follow a short link", description = """
            Redirects to the original URL and counts the click. This is the link visitors open.

            **"Try it out" here shows "Failed to fetch". That is expected, not an error:** the browser \
            follows the redirect to the original website, which does not allow Swagger UI's page to read \
            its response (CORS). The click is still counted.

            To test it, open `http://localhost:8080/{code}` in a browser tab, or run \
            `curl -i http://localhost:8080/{code}` to see the 302 and its `Location` header. \
            Use `GET /stats/{code}` to check the click count.""")
    @ApiResponse(responseCode = "302", description = "Redirect to the original URL",
            headers = @Header(name = "Location", description = "The original URL"))
    @ApiResponse(responseCode = "404", description = "Unknown code",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> redirect(@Parameter(example = "Ab3dE9x") @PathVariable String code) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(urlService.resolve(code)))
                .build();
    }

    @GetMapping("/stats/" + CODE)
    @Operation(summary = "Click statistics for a short link")
    @ApiResponse(responseCode = "200", description = "Statistics")
    @ApiResponse(responseCode = "404", description = "Unknown code",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public StatsResponse stats(@Parameter(example = "Ab3dE9x") @PathVariable String code) {
        UrlMapping mapping = urlService.stats(code);
        return new StatsResponse(mapping.getCode(), mapping.getOriginalUrl(), mapping.getClickCount(),
                mapping.getCreatedAt(), mapping.getLastAccessedAt());
    }
}
