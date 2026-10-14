package com.agentic.orchestrator.engine;

/** The pipeline definition is invalid: duplicate stage, unknown dependency, or a cycle. */
public class PipelineValidationException extends RuntimeException {

    public PipelineValidationException(String message) {
        super(message);
    }
}
