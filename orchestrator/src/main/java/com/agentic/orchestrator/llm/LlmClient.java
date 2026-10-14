package com.agentic.orchestrator.llm;

/**
 * The only boundary between agents and a language model. Agents are written against this
 * interface, so the same agent code runs with the live Claude API or with recorded responses.
 */
public interface LlmClient {

    /** Returns the model's text response, or throws {@link LlmException}. */
    String complete(LlmRequest request);

    /** Short label for logs, e.g. "claude-opus-5-5" or "scripted". */
    String describe();
}
