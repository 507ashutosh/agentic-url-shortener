package com.agentic.orchestrator.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.agentic.orchestrator.run.Decision.Kind;

class AgentOutputParserTest {

    @Test
    void parsesEverySection() {
        AgentOutput output = AgentOutputParser.parse("""
                SUMMARY: Added expiry
                DECISION: Use 410 Gone | BECAUSE: the link existed
                ASSUMPTION: N is per link
                QUESTION: Delete expired links?

                ## Report
                Some text.

                <<<FILE shortener/src/main/A.java
                class A {
                    // >>> inside code is fine when not alone on its line
                }
                >>>
                """);

        assertThat(output.summary()).isEqualTo("Added expiry");
        assertThat(output.decisions()).extracting(AgentOutput.ParsedDecision::kind)
                .containsExactly(Kind.DECISION, Kind.ASSUMPTION, Kind.QUESTION);
        assertThat(output.decisions().get(0).text()).isEqualTo("Use 410 Gone");
        assertThat(output.decisions().get(0).rationale()).isEqualTo("the link existed");
        assertThat(output.body()).isEqualTo("## Report\nSome text.");
        assertThat(output.files()).singleElement().satisfies(f -> {
            assertThat(f.path()).isEqualTo("shortener/src/main/A.java");
            assertThat(f.content()).startsWith("class A {").contains("// >>> inside code").endsWith("}\n");
        });
    }

    @Test
    void decisionWithoutReasonHasNoRationale() {
        AgentOutput output = AgentOutputParser.parse("SUMMARY: s\nDECISION: just do it\n");

        assertThat(output.decisions().get(0).rationale()).isNull();
    }

    @Test
    void rejectsAResponseWithoutSummary() {
        assertThatThrownBy(() -> AgentOutputParser.parse("Here is my answer"))
                .isInstanceOf(AgentOutputException.class)
                .hasMessageContaining("SUMMARY");
    }

    @Test
    void rejectsAnUnclosedFileBlock() {
        assertThatThrownBy(() -> AgentOutputParser.parse("SUMMARY: s\n<<<FILE a.txt\ncontent\n"))
                .isInstanceOf(AgentOutputException.class)
                .hasMessageContaining("not closed");
    }
}
