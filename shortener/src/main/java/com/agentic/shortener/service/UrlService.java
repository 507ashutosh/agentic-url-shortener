package com.agentic.shortener.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentic.shortener.domain.UrlMapping;
import com.agentic.shortener.domain.UrlMappingRepository;

/** The business rules: create a short link, resolve it for a redirect, report its stats. */
@Service
public class UrlService {

    static final int MAX_CODE_ATTEMPTS = 5;

    private final UrlMappingRepository repository;
    private final UrlValidator validator;
    private final CodeGenerator codeGenerator;
    private final Clock clock;

    public UrlService(UrlMappingRepository repository, UrlValidator validator,
                      CodeGenerator codeGenerator, Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    @Transactional
    public UrlMapping shorten(String url) {
        String validUrl = validator.validate(url);
        return repository.save(new UrlMapping(newUniqueCode(), validUrl, clock.instant()));
    }

    /** Returns the long URL for a code and counts the visit. */
    @Transactional
    public String resolve(String code) {
        UrlMapping mapping = find(code);
        repository.recordClick(code, clock.instant());
        return mapping.getOriginalUrl();
    }

    @Transactional(readOnly = true)
    public UrlMapping stats(String code) {
        return find(code);
    }

    private UrlMapping find(String code) {
        return repository.findByCode(code).orElseThrow(() -> new CodeNotFoundException(code));
    }

    // A random code can collide with an existing one; retry a bounded number of times.
    private String newUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.next();
            if (!repository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique code after " + MAX_CODE_ATTEMPTS + " attempts");
    }
}
