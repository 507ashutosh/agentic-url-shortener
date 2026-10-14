package com.agentic.orchestrator.pipeline;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.agentic.orchestrator.agent.Agent;
import com.agentic.orchestrator.agent.roles.DesignAgent;
import com.agentic.orchestrator.agent.roles.DocsAgent;
import com.agentic.orchestrator.agent.roles.ImpactAnalysisAgent;
import com.agentic.orchestrator.agent.roles.ImplementAgent;
import com.agentic.orchestrator.agent.roles.PlannerAgent;
import com.agentic.orchestrator.agent.roles.RequirementsAgent;
import com.agentic.orchestrator.agent.roles.TestAgent;
import com.agentic.orchestrator.engine.Pipeline;
import com.agentic.orchestrator.engine.RollbackHandler;
import com.agentic.orchestrator.engine.StageControls;
import com.agentic.orchestrator.governance.PolicyGate;
import com.agentic.orchestrator.governance.ReleaseApprovalGate;
import com.agentic.orchestrator.governance.approval.ApprovalGateway;
import com.agentic.orchestrator.governance.policy.BlastRadiusPolicy;
import com.agentic.orchestrator.governance.policy.DependencyChangePolicy;
import com.agentic.orchestrator.governance.policy.Policy;
import com.agentic.orchestrator.governance.policy.SchemaChangePolicy;
import com.agentic.orchestrator.governance.policy.SecretScanPolicy;
import com.agentic.orchestrator.governance.policy.TestIntegrityPolicy;
import com.agentic.orchestrator.llm.LlmClient;
import com.agentic.orchestrator.run.RunContext;
import com.agentic.orchestrator.run.WorkspaceCheckpoints;
import com.agentic.orchestrator.tools.ChangeSetBuilder;
import com.agentic.orchestrator.tools.TestRunner;

/**
 * Wires the agents and tools into the dependency graph and attaches the governance to each stage:
 * <pre>
 *                ┌ plan            ┐            ┌ implement ┐
 * requirements → ┤                 ├→ design → ┼ tests     ┼→ validate → release
 *                └ impact-analysis ┘            └ docs      ┘
 * </pre>
 * <ul>
 *   <li>agents: 3 attempts; agents that write files are checked by the policies after every attempt,
 *       committed when accepted and rolled back when not</li>
 *   <li>validate: 1 attempt (a build is deterministic); final secret scan; on failure the whole
 *       change set is rolled back to the baseline</li>
 *   <li>release: a human approves, rejects, or sends a stage back for revision</li>
 * </ul>
 */
public final class SdlcPipelineFactory {

    static final int AGENT_ATTEMPTS = 3;
    static final Duration RETRY_BACKOFF = Duration.ofMillis(500);
    static final int MAX_FILES_PER_STAGE = 15;

    /** The pipeline plus, for each stage, the workspace folders it writes (used to reset them for re-planning). */
    public record SdlcPipeline(Pipeline pipeline, Map<String, List<String>> writableFolders) {

        public List<String> foldersOf(String stageId) {
            return writableFolders.getOrDefault(stageId, List.of());
        }
    }

    private SdlcPipelineFactory() {
    }

    public static SdlcPipeline create(LlmClient llm, RunContext context, WorkspaceCheckpoints checkpoints,
                                      ApprovalGateway approvals) {
        List<Agent> agents = List.of(new RequirementsAgent(llm), new PlannerAgent(llm), new ImpactAnalysisAgent(llm),
                new DesignAgent(llm), new ImplementAgent(llm), new TestAgent(llm), new DocsAgent(llm));
        Map<String, List<String>> folders = new LinkedHashMap<>();
        agents.forEach(a -> folders.put(a.id(), a.writableFolders()));
        List<String> allWritable = folders.values().stream().flatMap(List::stream).distinct().toList();

        Map<String, StageControls> controls = new LinkedHashMap<>();
        for (Agent agent : agents) {
            controls.put(agent.id(), agentControls(agent, context, checkpoints, approvals));
        }

        TestRunner testRunner = new TestRunner();
        StageControls validate = StageControls.defaults()
                .withExitGate(new PolicyGate(List.of(new SecretScanPolicy()),
                        () -> checkpoints.changesSinceBaseline(allWritable), approvals, context, true))
                .withRollback(new RollbackHandler() {
                    @Override
                    public void commit(String stageId) {
                    }

                    @Override
                    public String rollback(String stageId) {
                        String undone = checkpoints.rollbackToBaseline(allWritable);
                        return undone == null ? null : undone + " back to the baseline";
                    }
                });

        ChangeSetBuilder changeSet = new ChangeSetBuilder();
        StageControls release = StageControls.defaults()
                .withExitGate(new ReleaseApprovalGate(approvals, context, List.copyOf(folders.keySet())));

        Pipeline.Builder builder = Pipeline.builder("sdlc-" + context.scenario().name());
        StageAdder add = (id, deps) -> {
            Agent agent = agents.stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
            builder.stage(id, () -> agent.run(context), controls.get(id), deps);
        };
        add.stage("requirements");
        add.stage("plan", "requirements");
        add.stage("impact-analysis", "requirements");
        add.stage("design", "plan", "impact-analysis");
        add.stage("implement", "design");
        add.stage("tests", "design");
        add.stage("docs", "design");
        builder.stage(TestRunner.STAGE_ID, () -> testRunner.run(context), validate, "implement", "tests", "docs");
        builder.stage(ChangeSetBuilder.STAGE_ID, () -> changeSet.run(context), release, TestRunner.STAGE_ID);
        return new SdlcPipeline(builder.build(), folders);
    }

    @FunctionalInterface
    private interface StageAdder {
        void stage(String agentId, String... dependsOn);
    }

    private static StageControls agentControls(Agent agent, RunContext context, WorkspaceCheckpoints checkpoints,
                                               ApprovalGateway approvals) {
        StageControls controls = StageControls.defaults().withRetries(AGENT_ATTEMPTS, RETRY_BACKOFF);
        List<String> folders = agent.writableFolders();
        if (folders.isEmpty()) {
            return controls;
        }
        List<Policy> policies = List.of(new SecretScanPolicy(), new TestIntegrityPolicy(), new SchemaChangePolicy(),
                new DependencyChangePolicy(), new BlastRadiusPolicy(MAX_FILES_PER_STAGE));
        return controls
                .withExitGate(new PolicyGate(policies, () -> checkpoints.uncommittedChanges(folders), approvals,
                        context, false))
                .withRollback(new RollbackHandler() {
                    @Override
                    public void commit(String stageId) {
                        checkpoints.commit("after " + stageId, folders);
                    }

                    @Override
                    public String rollback(String stageId) {
                        return checkpoints.rollback(folders);
                    }
                });
    }
}
