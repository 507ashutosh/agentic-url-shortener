package com.agentic.orchestrator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class DagExecutorTest {

    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private final DagExecutor executor = new DagExecutor(new RunListener() {
    });

    private StageAction recording(String id) {
        return () -> {
            events.add("start:" + id);
            Thread.sleep(20);
            events.add("end:" + id);
            return id + " done";
        };
    }

    @Test
    void everyStageStartsOnlyAfterItsDependenciesHaveFinished() {
        Pipeline pipeline = Pipeline.builder("order")
                .stage("requirements", recording("requirements"))
                .stage("design", recording("design"), "requirements")
                .stage("implement", recording("implement"), "design")
                .stage("tests", recording("tests"), "design")
                .stage("validate", recording("validate"), "implement", "tests")
                .build();

        RunReport report = executor.run(pipeline);

        assertThat(report.succeeded()).isTrue();
        for (String id : pipeline.topologicalOrder()) {
            for (String dep : pipeline.stage(id).dependsOn()) {
                assertThat(events.indexOf("end:" + dep))
                        .as(dep + " must finish before " + id + " starts")
                        .isLessThan(events.indexOf("start:" + id));
            }
        }
    }

    @Test
    void independentStagesRunAtTheSameTime() {
        // Each stage waits until all three have started. That only succeeds if they run concurrently;
        // run one after another, the first would wait forever (here: time out and fail).
        CountDownLatch allStarted = new CountDownLatch(3);
        StageAction waitForSiblings = () -> {
            allStarted.countDown();
            if (!allStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("siblings did not run in parallel");
            }
            return "ran alongside siblings";
        };
        Pipeline pipeline = Pipeline.builder("parallel")
                .stage("design", () -> "ok")
                .stage("implement", waitForSiblings, "design")
                .stage("tests", waitForSiblings, "design")
                .stage("docs", waitForSiblings, "design")
                .build();

        RunReport report = executor.run(pipeline);

        assertThat(report.succeeded()).isTrue();
    }

    @Test
    void aFailedStageSkipsItsDependentsButNotIndependentBranches() {
        Pipeline pipeline = Pipeline.builder("failure")
                .stage("design", () -> "ok")
                .stage("implement", () -> "ok", "design")
                .stage("tests", () -> {
                    throw new IllegalStateException("3 tests failing");
                }, "design")
                .stage("docs", () -> "ok", "design")
                .stage("validate", () -> "ok", "implement", "tests", "docs")
                .stage("release", () -> "ok", "validate")
                .build();

        RunReport report = executor.run(pipeline);

        assertThat(report.succeeded()).isFalse();
        assertThat(report.outcome("tests").status()).isEqualTo(StageStatus.FAILED);
        assertThat(report.outcome("tests").detail()).isEqualTo("3 tests failing");
        assertThat(report.outcome("implement").status()).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(report.outcome("docs").status()).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(report.outcome("validate").status()).isEqualTo(StageStatus.SKIPPED);
        assertThat(report.outcome("validate").detail()).isEqualTo("dependency 'tests' failed");
        assertThat(report.outcome("release").detail()).isEqualTo("dependency 'validate' skipped");
    }

    @Test
    void reportListsOutcomesInDependencyOrderWithTimings() {
        Pipeline pipeline = Pipeline.builder("timing")
                .stage("a", recording("a"))
                .stage("b", recording("b"), "a")
                .build();

        RunReport report = executor.run(pipeline);

        assertThat(report.outcomes()).extracting(StageOutcome::stageId).containsExactly("a", "b");
        assertThat(report.outcome("b").startedAt()).isGreaterThanOrEqualTo(report.outcome("a").duration());
        assertThat(report.wallClock()).isGreaterThanOrEqualTo(Duration.ofMillis(40));
    }

    @Test
    void listenerReceivesEveryEvent() {
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        RunListener listener = new RunListener() {
            @Override
            public void runStarted(Pipeline pipeline) {
                received.add("run-started");
            }

            @Override
            public void stageStarted(Stage stage, Duration startedAt) {
                received.add("started:" + stage.id());
            }

            @Override
            public void stageFinished(StageOutcome outcome) {
                received.add("finished:" + outcome.stageId() + ":" + outcome.status());
            }

            @Override
            public void runFinished(RunReport report) {
                received.add("run-finished");
            }
        };
        Pipeline pipeline = Pipeline.builder("events")
                .stage("a", () -> {
                    throw new IllegalStateException("boom");
                })
                .stage("b", () -> "ok", "a")
                .build();

        new DagExecutor(listener).run(pipeline);

        assertThat(received).containsExactly(
                "run-started", "started:a", "finished:a:FAILED", "finished:b:SKIPPED", "run-finished");
    }
}
