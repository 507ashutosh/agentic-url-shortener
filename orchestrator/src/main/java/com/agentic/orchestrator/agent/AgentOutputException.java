package com.agentic.orchestrator.agent;

/** The agent's response did not follow the required format. */
public class AgentOutputException extends RuntimeException {

    public AgentOutputException(String message) {
        super(message);
    }
}
