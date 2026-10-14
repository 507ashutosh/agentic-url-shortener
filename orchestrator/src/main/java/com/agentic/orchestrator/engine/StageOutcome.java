package com.agentic.orchestrator.engine;

import java.time.Duration;

/**
 * What happened to one stage in a run.
 *
 * @param detail    the action's summary, the failure message, or why it was skipped or blocked
 * @param startedAt time from the start of the run until this stage started
 * @param duration  how long the stage ran, across all attempts (zero if skipped)
 * @param attempts  how many attempts were made (0 if it never ran)
 * @param reused    true if this outcome was carried over from an earlier run instead of re-running
 */
public record StageOutcome(String stageId, StageStatus status, String detail,
                           Duration startedAt, Duration duration, int attempts, boolean reused) {

    static StageOutcome skipped(String stageId, String reason, Duration at) {
        return new StageOutcome(stageId, StageStatus.SKIPPED, reason, at, Duration.ZERO, 0, false);
    }

    StageOutcome asReused() {
        return new StageOutcome(stageId, status, detail, Duration.ZERO, Duration.ZERO, 0, true);
    }

    public int retries() {
        return Math.max(0, attempts - 1);
    }
}
