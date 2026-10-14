package com.agentic.orchestrator.demo;

import java.util.Optional;

import com.agentic.orchestrator.engine.Pipeline;
import com.agentic.orchestrator.engine.StageAction;

/**
 * The SDLC pipeline shape, with placeholder stages that just wait and report.
 * Real agents replace these placeholders in the next step.
 *
 * <pre>
 * requirements → design → ┬ implement ┐
 *                         ├ tests     ├→ validate → release
 *                         └ docs      ┘
 * </pre>
 */
public final class DemoPipeline {

    private DemoPipeline() {
    }

    /** @param failStage optionally, a stage that should fail, to show what happens downstream */
    public static Pipeline create(Optional<String> failStage) {
        return Pipeline.builder("sdlc-demo")
                .stage("requirements", work(300, "spec ready", "requirements", failStage))
                .stage("design", work(300, "design ready", "design", failStage), "requirements")
                .stage("implement", work(600, "code written", "implement", failStage), "design")
                .stage("tests", work(500, "tests written", "tests", failStage), "design")
                .stage("docs", work(400, "docs written", "docs", failStage), "design")
                .stage("validate", work(300, "all checks passed", "validate", failStage),
                        "implement", "tests", "docs")
                .stage("release", work(200, "release ready", "release", failStage), "validate")
                .build();
    }

    private static StageAction work(long millis, String summary, String id, Optional<String> failStage) {
        return () -> {
            Thread.sleep(millis);
            if (failStage.filter(id::equals).isPresent()) {
                throw new IllegalStateException("simulated failure");
            }
            return summary;
        };
    }
}
