package com.agentic.orchestrator.governance;

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

import com.agentic.orchestrator.agent.Agent;
import com.agentic.orchestrator.governance.approval.ApprovalDecision;
import com.agentic.orchestrator.governance.approval.ApprovalGateway;
import com.agentic.orchestrator.run.Artifact;
import com.agentic.orchestrator.run.Decision;
import com.agentic.orchestrator.run.RunContext;
import com.agentic.orchestrator.run.RunWorkspace;
import com.agentic.orchestrator.scenario.Scenario;

class ClarificationCheckpointTest {

    @TempDir
    Path repo;

    private RunContext context;
    private final List<String> requirementsRuns = new ArrayList<>();

    /** Stands in for the requirements agent: records each run and the feedback it saw. */
    private final Agent requirementsAgent = new Agent() {
        @Override
        public String id() {
            return "requirements";
        }

        @Override
        public String run(RunContext ctx) {
            requirementsRuns.add(String.join(" | ", ctx.feedbackFor("requirements")));
            ctx.record(new Artifact("requirements", "clarified", "", List.of()), List.of());
            return "clarified";
        }

        @Override
        public List<String> writableFolders() {
            return List.of();
        }
    };

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(repo.resolve("shortener"));
        Files.writeString(repo.resolve("shortener/pom.xml"), "<project/>");
        context = new RunContext(new Scenario("t", "ambiguous", "T", "Make it more reliable.", "repo"),
                RunWorkspace.create(repo, "t"));
    }

    private void requirementsWith(Decision.Kind... kinds) {
        List<Decision> decisions = new ArrayList<>();
        for (Decision.Kind kind : kinds) {
            decisions.add(new Decision("requirements", kind, kind + " text", null, List.of()));
        }
        context.record(new Artifact("requirements", "first pass", "", List.of()), decisions);
    }

    private ClarificationCheckpoint checkpoint(ApprovalGateway gateway) {
        return new ClarificationCheckpoint(requirementsAgent, gateway);
    }

    @Test
    void passesWithoutAskingWhenThereAreNoQuestions() {
        requirementsWith(Decision.Kind.ASSUMPTION);

        String result = checkpoint(request -> {
            throw new AssertionError("must not ask");
        }).run(context);

        assertThat(result).isEqualTo("no open questions");
    }

    @Test
    void acceptedAssumptionsAreRecordedAndRequirementsAreNotRerun() {
        requirementsWith(Decision.Kind.QUESTION, Decision.Kind.ASSUMPTION);

        String result = checkpoint(request -> ApprovalDecision.approved("abhi")).run(context);

        assertThat(result).contains("assumptions accepted by abhi");
        assertThat(requirementsRuns).isEmpty();
        assertThat(context.approvals()).singleElement()
                .satisfies(a -> assertThat(a.request().policyId()).isEqualTo(ClarificationCheckpoint.POLICY_ID));
    }

    @Test
    void answersAreGivenToTheRequirementsAgentWhichRunsAgain() {
        requirementsWith(Decision.Kind.QUESTION);

        String result = checkpoint(request -> ApprovalDecision.revise("abhi", "requirements", "focus on outages"))
                .run(context);

        assertThat(requirementsRuns).containsExactly("focus on outages");
        assertThat(result).startsWith("clarified by abhi");
    }

    @Test
    void rejectingTheRequirementFailsTheStage() {
        requirementsWith(Decision.Kind.QUESTION);

        assertThatThrownBy(() -> checkpoint(request -> ApprovalDecision.rejected("abhi")).run(context))
                .hasMessage("requirement rejected by abhi");
    }
}
