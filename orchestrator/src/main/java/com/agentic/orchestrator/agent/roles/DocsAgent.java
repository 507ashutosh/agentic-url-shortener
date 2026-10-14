package com.agentic.orchestrator.agent.roles;

import java.util.List;

import com.agentic.orchestrator.agent.LlmAgent;
import com.agentic.orchestrator.llm.LlmClient;

/** Documents the change for API users and maintainers. */
public final class DocsAgent extends LlmAgent {

    public DocsAgent(LlmClient llm) {
        super(llm);
    }

    @Override
    public String id() {
        return "docs";
    }

    @Override
    protected String role() {
        return """
                write user-facing documentation for the change as one Markdown file under docs/features/: \
                what it does, the API changes with example curl requests and responses, error cases, \
                and any configuration or migration notes.""";
    }

    @Override
    protected List<String> reads() {
        return List.of("requirements", "design");
    }

    @Override
    protected List<String> writableFolders() {
        return List.of("docs");
    }
}
