package com.agentic.orchestrator.agent.roles;

import java.util.List;

import com.agentic.orchestrator.agent.LlmAgent;
import com.agentic.orchestrator.llm.LlmClient;

/** Reads the existing code and identifies what the change touches (brownfield reasoning). */
public final class ImpactAnalysisAgent extends LlmAgent {

    public ImpactAnalysisAgent(LlmClient llm) {
        super(llm);
    }

    @Override
    public String id() {
        return "impact-analysis";
    }

    @Override
    protected String role() {
        return """
                analyse the current codebase against the clarified requirement. List the impacted files, \
                classes, API endpoints, database schema and data flows, and for each say what changes and why. \
                Call out backward-compatibility risks (existing clients, existing rows in the database) and \
                anything that is NOT impacted but might look like it is.""";
    }

    @Override
    protected List<String> reads() {
        return List.of("requirements");
    }

    @Override
    protected boolean readsCodebase() {
        return true;
    }
}
