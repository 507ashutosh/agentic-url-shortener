package com.agentic.orchestrator.agent;

import java.util.List;

/** An agent's response after parsing: summary, decisions, the free-text report and any files. */
public record AgentOutput(String summary, List<ParsedDecision> decisions, String body, List<FileChange> files) {

    public record ParsedDecision(com.agentic.orchestrator.run.Decision.Kind kind, String text, String rationale) {
    }

    /** The complete new content of one file, relative to the workspace root. */
    public record FileChange(String path, String content) {
    }
}
