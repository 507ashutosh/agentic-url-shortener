package com.agentic.orchestrator.engine;

public enum StageStatus {
    /** The action ran and returned normally. */
    SUCCEEDED,
    /** The action threw an exception. */
    FAILED,
    /** Not run, because a stage it depends on did not succeed. */
    SKIPPED
}
