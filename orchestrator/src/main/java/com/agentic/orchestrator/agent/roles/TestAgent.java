package com.agentic.orchestrator.agent.roles;

import java.util.List;

import com.agentic.orchestrator.agent.LlmAgent;
import com.agentic.orchestrator.llm.LlmClient;

/** Writes unit and integration tests from the design, in parallel with the implementation. */
public final class TestAgent extends LlmAgent {

    public TestAgent(LlmClient llm) {
        super(llm);
    }

    @Override
    public String id() {
        return "tests";
    }

    @Override
    protected String role() {
        return """
                write unit and integration tests for the acceptance criteria, working from the design's contract \
                (the implementation is being written at the same time and you cannot see it). Add new test \
                classes rather than rewriting existing ones. Use JUnit 5, AssertJ, Mockito and MockMvc in the \
                style of the existing tests.""";
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
        return List.of("shortener/src/test");
    }
}
