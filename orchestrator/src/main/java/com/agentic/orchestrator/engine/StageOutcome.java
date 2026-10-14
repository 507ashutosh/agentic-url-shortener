package com.agentic.orchestrator.engine;

import java.time.Duration;

/**
 * What happened to one stage in a run.
 *
 * @param detail      the action's summary, the failure message, or why it was skipped
 * @param startedAt   time from the start of the run until this stage started
 * @param duration    how long the stage ran (zero if skipped)
 */
public record StageOutcome(String stageId, StageStatus status, String detail,
                           Duration startedAt, Duration duration) {
}
