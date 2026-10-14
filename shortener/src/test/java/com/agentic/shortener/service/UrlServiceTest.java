package com.agentic.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.agentic.shortener.domain.UrlMapping;
import com.agentic.shortener.domain.UrlMappingRepository;

class UrlServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    private final UrlMappingRepository repository = mock(UrlMappingRepository.class);
    private final CodeGenerator codeGenerator = mock(CodeGenerator.class);
    private UrlService service;

    @BeforeEach
    void setUp() {
        service = new UrlService(repository, new UrlValidator(), codeGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any(UrlMapping.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void shortenSavesAMappingWithAGeneratedCode() {
        when(codeGenerator.next()).thenReturn("abc1234");

        UrlMapping saved = service.shorten("https://example.com");

        assertThat(saved.getCode()).isEqualTo("abc1234");
        assertThat(saved.getOriginalUrl()).isEqualTo("https://example.com");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void shortenRetriesWhenTheCodeIsAlreadyTaken() {
        when(codeGenerator.next()).thenReturn("taken01", "free002");
        when(repository.existsByCode("taken01")).thenReturn(true);

        assertThat(service.shorten("https://example.com").getCode()).isEqualTo("free002");
    }

    @Test
    void shortenGivesUpAfterTheMaximumNumberOfCollisions() {
        when(codeGenerator.next()).thenReturn("taken01");
        when(repository.existsByCode("taken01")).thenReturn(true);

        assertThatThrownBy(() -> service.shorten("https://example.com"))
                .isInstanceOf(IllegalStateException.class);
        verify(codeGenerator, times(UrlService.MAX_CODE_ATTEMPTS)).next();
        verify(repository, never()).save(any());
    }

    @Test
    void shortenRejectsAnInvalidUrlWithoutSaving() {
        assertThatThrownBy(() -> service.shorten("not-a-url")).isInstanceOf(InvalidUrlException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void resolveReturnsTheUrlAndRecordsTheClick() {
        when(repository.findByCode("abc1234"))
                .thenReturn(Optional.of(new UrlMapping("abc1234", "https://example.com", NOW)));

        assertThat(service.resolve("abc1234")).isEqualTo("https://example.com");
        verify(repository).recordClick("abc1234", NOW);
    }

    @Test
    void resolveThrowsForAnUnknownCode() {
        when(repository.findByCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("missing")).isInstanceOf(CodeNotFoundException.class);
        verify(repository, never()).recordClick(any(), any());
    }
}
