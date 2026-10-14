package com.agentic.orchestrator.observability;

import java.util.List;

/**
 * The measured facts of one orchestrator run, saved as runs/&lt;run-id&gt;/run-summary.json
 * and aggregated across runs by {@link MetricsReport}.
 *
 * @param wallClockMs       start to finish, including time spent waiting for humans
 * @param humanWaitMs       time spent waiting at approval prompts
 * @param agentTimeMs       wall clock minus human wait: how long the automation itself took
 * @param recoveryTimesMs   for each stage that failed and later succeeded: time from its first failure to success
 * @param unrecoveredFailures stages that failed and never recovered in this run
 */
public record RunMetrics(
        String runId,
        String scenario,
        String agents,
        String startedAt,
        String outcome,
        String stopReason,
        long wallClockMs,
        long humanWaitMs,
        long agentTimeMs,
        int stagesSucceeded,
        int stagesFailed,
        int stagesBlocked,
        int stagesSkipped,
        int stagesReused,
        int stageExecutions,
        int retries,
        int rollbacks,
        int fallbacks,
        int policyBlocks,
        int approvalsGranted,
        int approvalsRejected,
        int revisionsRequested,
        int replans,
        List<Long> recoveryTimesMs,
        int unrecoveredFailures,
        List<StageMetric> stages) {

    /** Final state of one stage in this run. */
    public record StageMetric(String stageId, String status, int attempts, long durationMs, boolean reused) {
    }

    public boolean succeeded() {
        return "OK".equals(outcome);
    }
}
