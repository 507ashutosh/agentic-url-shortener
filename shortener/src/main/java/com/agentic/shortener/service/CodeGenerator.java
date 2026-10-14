package com.agentic.shortener.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/** Produces random 7-character codes from letters and digits (62^7, about 3.5 trillion values). */
@Component
public class CodeGenerator {

    static final int LENGTH = 7;
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    // SecureRandom so codes cannot be guessed from earlier ones.
    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
