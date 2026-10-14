package com.agentic.orchestrator.engine;

public enum StageStatus {
    /** The action ran and every exit gate accepted it. */
    SUCCEEDED,
    /** Every attempt failed (exception or rejected output). */
    FAILED,
    /** A gate stopped it, e.g. a human rejected an approval. */
    BLOCKED,
    /** Not run: a dependency did not succeed, or the run was safe-stopped first. */
    SKIPPED
}
