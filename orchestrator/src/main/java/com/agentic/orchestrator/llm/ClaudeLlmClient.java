package com.agentic.orchestrator.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;

/**
 * Live mode: calls the Claude Messages API through the official Anthropic Java SDK.
 * Reads ANTHROPIC_API_KEY from the environment; the key never appears in code or config.
 */
public final class ClaudeLlmClient implements LlmClient {

    public static final String DEFAULT_MODEL = "claude-opus-5-5";

    // Generous, because thinking tokens count towards it and the implement agent writes whole files.
    private static final long MAX_TOKENS = 64_000;

    private final AnthropicClient client;
    private final String model;

    public ClaudeLlmClient(String model) {
        this.client = AnthropicOkHttpClient.fromEnv();
        this.model = model;
    }

    /** Uses ANTHROPIC_MODEL if set, otherwise {@link #DEFAULT_MODEL}. */
    public static ClaudeLlmClient fromEnv() {
        if (System.getenv("ANTHROPIC_API_KEY") == null) {
            throw new LlmException("ANTHROPIC_API_KEY is not set; run without --live to use mock mode");
        }
        String model = System.getenv().getOrDefault("ANTHROPIC_MODEL", DEFAULT_MODEL);
        return new ClaudeLlmClient(model);
    }

    @Override
    public String complete(LlmRequest request) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(request.systemPrompt())
                .addUserMessage(request.userPrompt())
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                // If a safety classifier declines the request, the API retries it on a suitable model.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();

        StringBuilder text = new StringBuilder();
        StopReason[] stopReason = new StopReason[1];
        // Streaming, so long responses are not cut off by HTTP timeouts.
        try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {
            stream.stream().forEach(event -> {
                event.contentBlockDelta()
                        .flatMap(delta -> delta.delta().text())
                        .ifPresent(delta -> text.append(delta.text()));
                event.messageDelta()
                        .flatMap(delta -> delta.delta().stopReason())
                        .ifPresent(reason -> stopReason[0] = reason);
            });
        } catch (RuntimeException e) {
            throw new LlmException("Claude API call failed for " + request.agentId() + ": " + e.getMessage(), e);
        }

        if (StopReason.REFUSAL.equals(stopReason[0])) {
            throw new LlmException("Claude declined the request for " + request.agentId());
        }
        if (StopReason.MAX_TOKENS.equals(stopReason[0])) {
            throw new LlmException("Response for " + request.agentId() + " was cut off at " + MAX_TOKENS + " tokens");
        }
        return text.toString();
    }

    @Override
    public String describe() {
        return model;
    }
}
