package com.agentic.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CodeGeneratorTest {

    private final CodeGenerator generator = new CodeGenerator();

    @Test
    void codesHaveTheExpectedLengthAndCharacters() {
        for (int i = 0; i < 100; i++) {
            assertThat(generator.next()).matches("[0-9A-Za-z]{" + CodeGenerator.LENGTH + "}");
        }
    }

    @Test
    void codesDoNotRepeatInASmallSample() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            codes.add(generator.next());
        }
        assertThat(codes).hasSize(1_000);
    }
}
