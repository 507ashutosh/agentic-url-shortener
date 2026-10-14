package com.agentic.orchestrator.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class FallbackLlmClientTest {

    private static LlmClient fixed(String name, String answer) {
        return new LlmClient() {
            @Override
            public String complete(LlmRequest request) {
                return answer;
            }

            @Override
            public String describe() {
                return name;
            }
        };
    }

    private static final LlmRequest DESIGN = new LlmRequest("design", "system", "prompt");
    private static final LlmRequest TESTS = new LlmRequest("tests", "system", "prompt");

    @Test
    void usesThePrimaryWhileItWorks() {
        FallbackLlmClient client = new FallbackLlmClient(fixed("live", "live answer"), fixed("mock", "recorded"), 2,
                (agent, reason) -> {
                });

        assertThat(client.complete(DESIGN)).isEqualTo("live answer");
    }

    @Test
    void switchesAnAgentToTheFallbackAfterRepeatedFailures() {
        List<String> notices = new ArrayList<>();
        LlmClient outage = new FaultInjectingLlmClient(fixed("live", "live answer"),
                List.of(FaultInjectingLlmClient.Fault.parse("design:outage")));
        FallbackLlmClient client = new FallbackLlmClient(outage, fixed("mock", "recorded"), 2,
                (agent, reason) -> notices.add(agent));

        assertThatThrownBy(() -> client.complete(DESIGN)).isInstanceOf(LlmException.class);
        assertThatThrownBy(() -> client.complete(DESIGN)).isInstanceOf(LlmException.class);
        assertThat(client.complete(DESIGN)).isEqualTo("recorded");

        assertThat(notices).containsExactly("design");
        assertThat(client.complete(TESTS)).isEqualTo("live answer");   // other agents are unaffected
    }

    @Test
    void injectedFaultsAreParsedAndAppliedOnlyToTheirAgent() {
        LlmClient client = new FaultInjectingLlmClient(fixed("mock", "SUMMARY: s\n<<<FILE docs/a.md\ntext\n>>>\n"),
                List.of(FaultInjectingLlmClient.Fault.parse("docs:secret"),
                        FaultInjectingLlmClient.Fault.parse("tests:error")));

        assertThat(client.complete(new LlmRequest("docs", "s", "p"))).contains("AKIA");
        assertThat(client.complete(new LlmRequest("docs", "s", "p"))).doesNotContain("AKIA");   // only once
        assertThatThrownBy(() -> client.complete(TESTS)).isInstanceOf(LlmException.class);
        assertThat(client.complete(TESTS)).startsWith("SUMMARY");
        assertThat(client.complete(DESIGN)).startsWith("SUMMARY");
    }
}
