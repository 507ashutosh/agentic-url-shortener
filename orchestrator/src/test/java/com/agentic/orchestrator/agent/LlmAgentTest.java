package com.agentic.orchestrator.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.agentic.orchestrator.llm.LlmClient;
import com.agentic.orchestrator.llm.LlmRequest;
import com.agentic.orchestrator.run.Artifact;
import com.agentic.orchestrator.run.Decision;
import com.agentic.orchestrator.run.RunContext;
import com.agentic.orchestrator.run.RunWorkspace;
import com.agentic.orchestrator.scenario.Scenario;

class LlmAgentTest {

    @TempDir
    Path repo;

    private RunContext context;
    private final List<LlmRequest> requests = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(repo.resolve("shortener/src/main/java"));
        Files.writeString(repo.resolve("shortener/pom.xml"), "<project/>");
        Files.writeString(repo.resolve("shortener/src/main/java/App.java"), "class App {}");
        Scenario scenario = new Scenario("test", "brownfield", "Test", "Links should expire after N days.");
        context = new RunContext(scenario, RunWorkspace.create(repo, "test"));
    }

    /** A fake model that records the request and returns a fixed response. */
    private LlmClient answering(String response) {
        return new LlmClient() {
            @Override
            public String complete(LlmRequest request) {
                requests.add(request);
                return response;
            }

            @Override
            public String describe() {
                return "fake";
            }
        };
    }

    /** A minimal agent: reads "design", may write only under shortener/src/main. */
    private static final class CodingAgent extends LlmAgent {

        CodingAgent(LlmClient llm) {
            super(llm);
        }

        @Override
        public String id() {
            return "implement";
        }

        @Override
        protected String role() {
            return "implement the design";
        }

        @Override
        protected List<String> reads() {
            return List.of("design");
        }

        @Override
        protected boolean readsCodebase() {
            return true;
        }

        @Override
        protected List<String> writableFolders() {
            return List.of("shortener/src/main");
        }
    }

    @Test
    void promptContainsTheRequirementUpstreamOutputAndCode() {
        context.record(new Artifact("design", "add expires_at", "Design details here", List.of()), List.of());

        new CodingAgent(answering("SUMMARY: done\n")).run(context);

        String prompt = requests.get(0).userPrompt();
        assertThat(prompt).contains("Links should expire after N days.")
                .contains("Output of the design stage").contains("Design details here")
                .contains("shortener/src/main/java/App.java").contains("class App {}");
        assertThat(requests.get(0).systemPrompt()).contains("only create or change files under: shortener/src/main");
    }

    @Test
    void writesFilesAndRecordsArtifactWithDecisionLineage() throws IOException {
        String summary = new CodingAgent(answering("""
                SUMMARY: added Expiry
                DECISION: new class | BECAUSE: separation
                <<<FILE shortener/src/main/java/Expiry.java
                class Expiry {}
                >>>
                """)).run(context);

        assertThat(summary).isEqualTo("added Expiry");
        Path written = context.workspace().workspaceDir().resolve("shortener/src/main/java/Expiry.java");
        assertThat(Files.readString(written)).isEqualTo("class Expiry {}\n");
        assertThat(context.artifact("implement")).get()
                .extracting(Artifact::filesWritten).asList().containsExactly("shortener/src/main/java/Expiry.java");
        assertThat(context.decisions()).singleElement().satisfies(d -> {
            assertThat(d.stageId()).isEqualTo("implement");
            assertThat(d.kind()).isEqualTo(Decision.Kind.DECISION);
            assertThat(d.basedOn()).containsExactly("design");
        });
        assertThat(context.workspace().artifactsDir().resolve("implement.md")).exists();
    }

    @Test
    void refusesToWriteOutsideItsAllowedFolders() {
        CodingAgent agent = new CodingAgent(answering("""
                SUMMARY: sneaky
                <<<FILE shortener/src/test/java/FakeTest.java
                class FakeTest {}
                >>>
                """));

        assertThatThrownBy(() -> agent.run(context))
                .isInstanceOf(AutonomyBoundaryException.class)
                .hasMessageContaining("shortener/src/test/java/FakeTest.java");
    }

    @Test
    void refusesPathTraversalOutOfTheWorkspace() {
        CodingAgent agent = new CodingAgent(answering("""
                SUMMARY: escape
                <<<FILE shortener/src/main/../../../../../etc/evil
                x
                >>>
                """));

        assertThatThrownBy(() -> agent.run(context)).isInstanceOf(AutonomyBoundaryException.class);
    }

    @Test
    void workspaceIsACopySoTheRealCodeIsNeverTouched() throws IOException {
        new CodingAgent(answering("""
                SUMMARY: changed App
                <<<FILE shortener/src/main/java/App.java
                class App { int changed; }
                >>>
                """)).run(context);

        assertThat(Files.readString(repo.resolve("shortener/src/main/java/App.java"))).isEqualTo("class App {}");
    }
}
