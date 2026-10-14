package com.agentic.orchestrator.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** The one JSON configuration used for audit files and metrics. */
final class Json {

    /** Compact, one object per line, for the append-only audit log. */
    static final ObjectMapper LINE = new ObjectMapper();

    /** Indented, for files people read. */
    static final ObjectMapper PRETTY = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private Json() {
    }
}
