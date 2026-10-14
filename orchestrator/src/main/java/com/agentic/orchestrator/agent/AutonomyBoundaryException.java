package com.agentic.orchestrator.agent;

/** An agent tried to write outside the folders it is allowed to change. */
public class AutonomyBoundaryException extends RuntimeException {

    public AutonomyBoundaryException(String message) {
        super(message);
    }
}
