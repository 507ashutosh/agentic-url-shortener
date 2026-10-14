package com.agentic.orchestrator.agent.roles;

import java.util.List;

import com.agentic.orchestrator.agent.LlmAgent;
import com.agentic.orchestrator.llm.LlmClient;

/** Turns a raw requirement into a clear problem statement with acceptance criteria. */
public final class RequirementsAgent extends LlmAgent {

    public RequirementsAgent(LlmClient llm) {
        super(llm);
    }

    @Override
    public String id() {
        return "requirements";
    }

    @Override
    protected String role() {
        return """
                interpret the requirement and normalise it into a clear engineering problem. \
                State the intent, the scope (in and out), and numbered, testable acceptance criteria. \
                Identify every ambiguity: resolve each one with a reasonable ASSUMPTION, and add a QUESTION \
                where a human should confirm.""";
    }

    @Override
    protected List<String> reads() {
        return List.of();
    }
}
