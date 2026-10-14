package com.agentic.orchestrator.engine;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The result of running a whole pipeline. Outcomes are listed in dependency order.
 *
 * @param stopReason why the run was safe-stopped, or null if it was not
 */
public record RunReport(String pipelineName, List<StageOutcome> outcomes, Duration wallClock, String stopReason) {

    public RunReport {
        outcomes = List.copyOf(outcomes);
    }

    public boolean succeeded() {
        return outcomes.stream().allMatch(o -> o.status() == StageStatus.SUCCEEDED);
    }

    public Optional<String> safeStop() {
        return Optional.ofNullable(stopReason);
    }

    public long count(StageStatus status) {
        return outcomes.stream().filter(o -> o.status() == status).count();
    }

    public int retries() {
        return outcomes.stream().mapToInt(StageOutcome::retries).sum();
    }

    public long reusedCount() {
        return outcomes.stream().filter(StageOutcome::reused).count();
    }

    public StageOutcome outcome(String stageId) {
        return outcomes.stream().filter(o -> o.stageId().equals(stageId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No stage '" + stageId + "' in this run"));
    }

    /** Total time the stages spent working. Compared with wall clock, shows what parallelism saved. */
    public Duration sumOfStageDurations() {
        return outcomes.stream().map(StageOutcome::duration).reduce(Duration.ZERO, Duration::plus);
    }
}
