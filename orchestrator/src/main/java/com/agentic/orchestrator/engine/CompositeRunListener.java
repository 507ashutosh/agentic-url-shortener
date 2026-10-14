package com.agentic.orchestrator.engine;

import java.time.Duration;
import java.util.List;

/** Sends every event to several listeners in order, e.g. the console and the audit log. */
public final class CompositeRunListener implements RunListener {

    private final List<RunListener> listeners;

    public CompositeRunListener(RunListener... listeners) {
        this.listeners = List.of(listeners);
    }

    @Override
    public void runStarted(Pipeline pipeline) {
        listeners.forEach(l -> l.runStarted(pipeline));
    }

    @Override
    public void replanning(String changedStage, List<String> rerun, List<String> reused) {
        listeners.forEach(l -> l.replanning(changedStage, rerun, reused));
    }

    @Override
    public void stageStarted(Stage stage, Duration startedAt) {
        listeners.forEach(l -> l.stageStarted(stage, startedAt));
    }

    @Override
    public void gateFailed(Stage stage, Gate gate, GateDecision decision) {
        listeners.forEach(l -> l.gateFailed(stage, gate, decision));
    }

    @Override
    public void stageRetrying(Stage stage, int failedAttempt, int maxAttempts, String reason) {
        listeners.forEach(l -> l.stageRetrying(stage, failedAttempt, maxAttempts, reason));
    }

    @Override
    public void stageRolledBack(Stage stage, String whatWasUndone) {
        listeners.forEach(l -> l.stageRolledBack(stage, whatWasUndone));
    }

    @Override
    public void stageFinished(StageOutcome outcome) {
        listeners.forEach(l -> l.stageFinished(outcome));
    }

    @Override
    public void safeStop(String reason) {
        listeners.forEach(l -> l.safeStop(reason));
    }

    @Override
    public void runFinished(RunReport report) {
        listeners.forEach(l -> l.runFinished(report));
    }
}
