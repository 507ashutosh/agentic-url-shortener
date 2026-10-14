package com.agentic.orchestrator.agent.roles;

import java.util.List;

import com.agentic.orchestrator.agent.LlmAgent;
import com.agentic.orchestrator.llm.LlmClient;

/** Breaks the clarified requirement into actionable tasks with dependencies and order. */
public final class PlannerAgent extends LlmAgent {

    public PlannerAgent(LlmClient llm) {
        super(llm);
    }

    @Override
    public String id() {
        return "plan";
    }

    @Override
    protected String role() {
        return """
                decompose the clarified requirement into small, actionable engineering tasks. \
                Give each task an id (T1, T2, ...), a one-line description, the SDLC stage that owns it \
                (design, implement, tests, docs, validate, release) and the task ids it depends on. \
                Present them as a Markdown table, then note which tasks can run in parallel.""";
    }

    @Override
    protected List<String> reads() {
        return List.of("requirements");
    }
}
