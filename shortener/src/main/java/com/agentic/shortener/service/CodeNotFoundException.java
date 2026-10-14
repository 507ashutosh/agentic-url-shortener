package com.agentic.shortener.service;

public class CodeNotFoundException extends RuntimeException {

    public CodeNotFoundException(String code) {
        super("No short link found for code '" + code + "'");
    }
}
