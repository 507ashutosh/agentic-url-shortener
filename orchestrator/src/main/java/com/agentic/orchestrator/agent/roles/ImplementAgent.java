package com.agentic.orchestrator.agent.roles;

import java.util.List;

import com.agentic.orchestrator.agent.LlmAgent;
import com.agentic.orchestrator.llm.LlmClient;

/** Changes the production code (never the tests) according to the design. */
public final class ImplementAgent extends LlmAgent {

    public ImplementAgent(LlmClient llm) {
        super(llm);
    }

    @Override
    public String id() {
        return "implement";
    }

    @Override
    protected String role() {
        return """
                implement the design in the production code exactly as specified (names, fields, status codes), \
                so it matches tests that another agent is writing from the same design. Keep existing behaviour \
                and existing public method signatures working. Follow the style of the surrounding code. \
                Output every file you change in full.""";
    }

    @Override
    protected List<String> reads() {
        return List.of("requirements", "design");
    }

    @Override
    protected boolean readsCodebase() {
        return true;
    }

    @Override
    public List<String> writableFolders() {
        return List.of("shortener/src/main");
    }
}
