package com.agentic.orchestrator.llm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Fallback with a per-agent circuit breaker: after the primary model has failed
 * {@code failuresBeforeFallback} times for an agent, that agent's calls go to the fallback
 * (the recorded, deterministic responses) for the rest of the run.
 * Combined with the engine's retries: attempt 1 and 2 use the live model, attempt 3 the fallback.
 */
public final class FallbackLlmClient implements LlmClient {

    private final LlmClient primary;
    private final LlmClient fallback;
    private final int failuresBeforeFallback;
    private final BiConsumer<String, String> onFallback;
    private final Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();

    /** @param onFallback told (agent id, reason) the first time an agent switches to the fallback */
    public FallbackLlmClient(LlmClient primary, LlmClient fallback, int failuresBeforeFallback,
                             BiConsumer<String, String> onFallback) {
        this.primary = primary;
        this.fallback = fallback;
        this.failuresBeforeFallback = failuresBeforeFallback;
        this.onFallback = onFallback;
    }

    @Override
    public String complete(LlmRequest request) {
        AtomicInteger count = failures.computeIfAbsent(request.agentId(), k -> new AtomicInteger());
        if (count.get() >= failuresBeforeFallback) {
            return fallback.complete(request);
        }
        try {
            return primary.complete(request);
        } catch (LlmException e) {
            if (count.incrementAndGet() == failuresBeforeFallback) {
                onFallback.accept(request.agentId(), "primary model failed " + failuresBeforeFallback
                        + " times; switching to " + fallback.describe());
            }
            throw e;
        }
    }

    @Override
    public String describe() {
        return primary.describe() + " (fallback: " + fallback.describe() + ")";
    }
}
