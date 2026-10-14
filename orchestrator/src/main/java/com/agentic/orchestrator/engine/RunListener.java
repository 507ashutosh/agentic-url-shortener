package com.agentic.orchestrator.engine;

import java.time.Duration;
import java.util.List;

/**
 * Receives events while a pipeline runs. The console printer uses it, and so will the audit log.
 * Methods can be called from several threads at once, because stages run in parallel.
 */
public interface RunListener {

    default void runStarted(Pipeline pipeline) {
    }

    /** A re-run after an upstream change: which stages run again and which results are reused. */
    default void replanning(String changedStage, List<String> rerun, List<String> reused) {
    }

    default void stageStarted(Stage stage, Duration startedAt) {
    }

    /** A gate did not pass. Passing gates are not reported. */
    default void gateFailed(Stage stage, Gate gate, GateDecision decision) {
    }

    /** An attempt failed and another one will follow. */
    default void stageRetrying(Stage stage, int failedAttempt, int maxAttempts, String reason) {
    }

    /** A failed or rejected attempt's effects were undone. */
    default void stageRolledBack(Stage stage, String whatWasUndone) {
    }

    default void stageFinished(StageOutcome outcome) {
    }

    /** The run will not start any more stages. Stages already running finish normally. */
    default void safeStop(String reason) {
    }

    default void runFinished(RunReport report) {
    }
}
