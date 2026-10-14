package com.agentic.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com", "http://example.com/a/b?c=d#e", "HTTPS://EXAMPLE.COM"})
    void acceptsHttpAndHttpsUrls(String url) {
        assertThat(validator.validate(url)).isEqualTo(url);
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(validator.validate("  https://example.com  ")).isEqualTo("https://example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingUrl(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessage("url is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "file:///etc/passwd", "ftp://example.com", "example.com"})
    void rejectsNonWebSchemes(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("http:// or https://");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://", "http:///path", "https://exa mple.com"})
    void rejectsMalformedUrls(String url) {
        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsUrlsLongerThanTheLimit() {
        String tooLong = "https://example.com/" + "a".repeat(UrlValidator.MAX_LENGTH);
        assertThatThrownBy(() -> validator.validate(tooLong))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("at most");
    }
}
