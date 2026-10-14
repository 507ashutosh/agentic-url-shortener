package com.agentic.orchestrator.engine;

import java.time.Duration;

/**
 * Receives events while a pipeline runs. The console printer uses it now; the audit log will too.
 * Methods can be called from several threads at once, because stages run in parallel.
 */
public interface RunListener {

    default void runStarted(Pipeline pipeline) {
    }

    default void stageStarted(Stage stage, Duration startedAt) {
    }

    default void stageFinished(StageOutcome outcome) {
    }

    default void runFinished(RunReport report) {
    }
}
