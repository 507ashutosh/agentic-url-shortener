package com.agentic.orchestrator.agent.roles;

import java.util.List;

import com.agentic.orchestrator.agent.LlmAgent;
import com.agentic.orchestrator.llm.LlmClient;

/** Decides how to build it: API contract, schema change and class-level design. */
public final class DesignAgent extends LlmAgent {

    public DesignAgent(LlmClient llm) {
        super(llm);
    }

    @Override
    public String id() {
        return "design";
    }

    @Override
    protected String role() {
        return """
                produce the technical design that the implement, tests and docs agents will each work from \
                independently and in parallel, so it must be precise enough that they agree without talking: \
                the exact API contract (request/response fields, status codes), the exact schema change, and \
                the exact class, method and exception names to add or change. Record each significant choice \
                as a DECISION with its reason.""";
    }

    @Override
    protected List<String> reads() {
        return List.of("requirements", "plan", "impact-analysis");
    }

    @Override
    protected boolean readsCodebase() {
        return true;
    }
}
