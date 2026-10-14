package com.agentic.orchestrator.run;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.agentic.orchestrator.scenario.Scenario;

/**
 * State shared by all stages of one run: the requirement, the workspace, every artifact
 * produced so far, and the decision lineage. Thread-safe, because parallel stages write to it.
 */
public final class RunContext {

    private final Scenario scenario;
    private final RunWorkspace workspace;
    private final Map<String, Artifact> artifacts = new ConcurrentHashMap<>();
    private final List<Decision> decisions = Collections.synchronizedList(new ArrayList<>());

    public RunContext(Scenario scenario, RunWorkspace workspace) {
        this.scenario = scenario;
        this.workspace = workspace;
    }

    public Scenario scenario() {
        return scenario;
    }

    public RunWorkspace workspace() {
        return workspace;
    }

    /** Stores a stage's output and its decisions, and saves the report to the run folder. */
    public void record(Artifact artifact, List<Decision> stageDecisions) {
        artifacts.put(artifact.stageId(), artifact);
        decisions.addAll(stageDecisions);
        workspace.saveArtifact(artifact, stageDecisions);
    }

    public Optional<Artifact> artifact(String stageId) {
        return Optional.ofNullable(artifacts.get(stageId));
    }

    public List<Decision> decisions() {
        synchronized (decisions) {
            return List.copyOf(decisions);
        }
    }
}
