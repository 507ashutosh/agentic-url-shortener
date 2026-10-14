package com.agentic.orchestrator.pipeline;

import com.agentic.orchestrator.agent.Agent;
import com.agentic.orchestrator.agent.roles.DesignAgent;
import com.agentic.orchestrator.agent.roles.DocsAgent;
import com.agentic.orchestrator.agent.roles.ImpactAnalysisAgent;
import com.agentic.orchestrator.agent.roles.ImplementAgent;
import com.agentic.orchestrator.agent.roles.PlannerAgent;
import com.agentic.orchestrator.agent.roles.RequirementsAgent;
import com.agentic.orchestrator.agent.roles.TestAgent;
import com.agentic.orchestrator.engine.Pipeline;
import com.agentic.orchestrator.engine.StageAction;
import com.agentic.orchestrator.llm.LlmClient;
import com.agentic.orchestrator.run.RunContext;
import com.agentic.orchestrator.tools.ChangeSetBuilder;
import com.agentic.orchestrator.tools.TestRunner;

/**
 * Wires the agents and tools into the dependency graph:
 * <pre>
 *                ┌ plan            ┐            ┌ implement ┐
 * requirements → ┤                 ├→ design → ┼ tests     ┼→ validate → release
 *                └ impact-analysis ┘            └ docs      ┘
 * </pre>
 */
public final class SdlcPipelineFactory {

    private SdlcPipelineFactory() {
    }

    public static Pipeline create(LlmClient llm, RunContext context) {
        TestRunner testRunner = new TestRunner();
        ChangeSetBuilder changeSet = new ChangeSetBuilder();
        return Pipeline.builder("sdlc-" + context.scenario().name())
                .stage("requirements", agent(new RequirementsAgent(llm), context))
                .stage("plan", agent(new PlannerAgent(llm), context), "requirements")
                .stage("impact-analysis", agent(new ImpactAnalysisAgent(llm), context), "requirements")
                .stage("design", agent(new DesignAgent(llm), context), "plan", "impact-analysis")
                .stage("implement", agent(new ImplementAgent(llm), context), "design")
                .stage("tests", agent(new TestAgent(llm), context), "design")
                .stage("docs", agent(new DocsAgent(llm), context), "design")
                .stage(TestRunner.STAGE_ID, () -> testRunner.run(context), "implement", "tests", "docs")
                .stage(ChangeSetBuilder.STAGE_ID, () -> changeSet.run(context), TestRunner.STAGE_ID)
                .build();
    }

    private static StageAction agent(Agent agent, RunContext context) {
        return () -> agent.run(context);
    }
}
