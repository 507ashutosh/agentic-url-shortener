package com.agentic.orchestrator.llm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Mock mode: returns a recorded response for each agent from
 * src/main/resources/mock/&lt;scenario&gt;/&lt;agent&gt;.txt. Deterministic and needs no API key,
 * but the agents still build prompts, parse the response and write files exactly as in live mode.
 */
public final class ScriptedLlmClient implements LlmClient {

    private final String scenario;

    public ScriptedLlmClient(String scenario) {
        this.scenario = scenario;
    }

    @Override
    public String complete(LlmRequest request) {
        String resource = "/mock/" + scenario + "/" + request.agentId() + ".txt";
        try (InputStream in = ScriptedLlmClient.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new LlmException("No recorded response at " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String describe() {
        return "scripted (mock/" + scenario + ")";
    }
}
