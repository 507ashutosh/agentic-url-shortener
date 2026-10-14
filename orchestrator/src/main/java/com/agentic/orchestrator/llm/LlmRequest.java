package com.agentic.orchestrator.llm;

/**
 * One call to a language model.
 *
 * @param agentId identifies the caller; the scripted client uses it to pick a recorded response
 */
public record LlmRequest(String agentId, String systemPrompt, String userPrompt) {
}
