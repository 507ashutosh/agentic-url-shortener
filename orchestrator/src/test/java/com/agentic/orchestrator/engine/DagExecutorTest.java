package com.agentic.orchestrator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** Fails the first {@code failures} calls, then succeeds. */
    private static StageAction failingFirst(int failures, AtomicInteger calls) {
        return () -> {
            if (calls.incrementAndGet() <= failures) {
                throw new IllegalStateException("transient failure " + calls.get());
            }
            return "ok after " + calls.get();
        };
    }

    /** Records commits and rollbacks, to check the engine calls them at the right moments. */
    private final class RecordingRollback implements RollbackHandler {

        @Override
        public void commit(String stageId) {
            events.add("commit:" + stageId);
        }

        @Override
        public String rollback(String stageId) {
            events.add("rollback:" + stageId);
            return "undone";
        }
    }

    // --- scheduling ---

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

        assertThat(executor.run(pipeline).succeeded()).isTrue();
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

    // --- retries ---

    @Test
    void aStageIsRetriedUntilItSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        Pipeline pipeline = Pipeline.builder("retry")
                .stage("tests", failingFirst(2, calls), StageControls.defaults().withRetries(3, Duration.ZERO))
                .build();

        RunReport report = executor.run(pipeline);

        assertThat(report.succeeded()).isTrue();
        assertThat(report.outcome("tests").attempts()).isEqualTo(3);
        assertThat(report.retries()).isEqualTo(2);
    }

    @Test
    void retriesAreBoundedAndExhaustionFailsTheStage() {
        AtomicInteger calls = new AtomicInteger();
        Pipeline pipeline = Pipeline.builder("bounded")
                .stage("tests", failingFirst(99, calls), StageControls.defaults().withRetries(3, Duration.ZERO))
                .build();

        RunReport report = executor.run(pipeline);

        assertThat(calls.get()).isEqualTo(3);
        assertThat(report.outcome("tests").status()).isEqualTo(StageStatus.FAILED);
        assertThat(report.outcome("tests").detail()).isEqualTo("transient failure 3");
        assertThat(report.safeStop()).contains("tests failed after 3 attempts");
    }

    // --- safe-stop ---

    @Test
    void aFailureSafeStopsTheRunButLetsRunningStagesFinish() {
        Pipeline pipeline = Pipeline.builder("failure")
                .stage("design", () -> "ok")
                .stage("implement", () -> "ok", "design")
                .stage("tests", () -> {
                    Thread.sleep(50);   // let the sibling stages start first
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
        assertThat(report.outcome("validate").detail()).isEqualTo("safe-stop: tests failed after 1 attempt");
        assertThat(report.outcome("release").status()).isEqualTo(StageStatus.SKIPPED);
    }

    // --- gates and rollback ---

    @Test
    void rejectedOutputIsRolledBackAndRetriedThenCommitted() {
        AtomicInteger checks = new AtomicInteger();
        Gate rejectFirst = Gate.of("policy", stage -> checks.incrementAndGet() == 1
                ? GateDecision.retry("secret found") : GateDecision.pass());
        Pipeline pipeline = Pipeline.builder("gated")
                .stage("docs", () -> "docs written", StageControls.defaults()
                        .withRetries(3, Duration.ZERO).withExitGate(rejectFirst).withRollback(new RecordingRollback()))
                .build();

        RunReport report = executor.run(pipeline);

        assertThat(report.outcome("docs").status()).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(report.outcome("docs").attempts()).isEqualTo(2);
        assertThat(events).containsExactly("rollback:docs", "commit:docs");
    }

    @Test
    void anExitGateStopBlocksTheStageRollsItBackAndSafeStops() {
        Gate humanSaysNo = Gate.of("approval", stage -> GateDecision.stop("rejected by abhi"));
        Pipeline pipeline = Pipeline.builder("rejected")
                .stage("implement", () -> "code written", StageControls.defaults()
                        .withRetries(3, Duration.ZERO).withExitGate(humanSaysNo).withRollback(new RecordingRollback()))
                .stage("release", () -> "ok", "implement")
                .build();

        RunReport report = executor.run(pipeline);

        assertThat(report.outcome("implement").status()).isEqualTo(StageStatus.BLOCKED);
        assertThat(report.outcome("implement").attempts()).isEqualTo(1);   // a stop is not retried
        assertThat(events).containsExactly("rollback:implement");
        assertThat(report.outcome("release").status()).isEqualTo(StageStatus.SKIPPED);
        assertThat(report.safeStop()).contains("implement: rejected by abhi");
    }

    @Test
    void anEntryGateStopPreventsTheStageFromRunning() {
        AtomicInteger runs = new AtomicInteger();
        Pipeline pipeline = Pipeline.builder("entry")
                .stage("release", () -> "released " + runs.incrementAndGet(), StageControls.defaults()
                        .withEntryGate(Gate.of("change-freeze", stage -> GateDecision.stop("change freeze"))))
                .build();

        RunReport report = executor.run(pipeline);

        assertThat(runs.get()).isZero();
        assertThat(report.outcome("release").status()).isEqualTo(StageStatus.BLOCKED);
    }

    @Test
    void aGateThatThrowsIsTreatedAsAStop() {
        Gate broken = Gate.of("broken", stage -> {
            throw new IllegalStateException("policy service down");
        });
        Pipeline pipeline = Pipeline.builder("broken-gate")
                .stage("a", () -> "ok", StageControls.defaults().withRetries(3, Duration.ZERO).withExitGate(broken))
                .build();

        RunReport report = executor.run(pipeline);

        assertThat(report.outcome("a").status()).isEqualTo(StageStatus.BLOCKED);
        assertThat(report.outcome("a").detail()).contains("could not be evaluated").contains("policy service down");
    }

    // --- re-planning ---

    @Test
    void rerunRepeatsOnlyTheChangedStageAndItsDownstream() {
        AtomicInteger aRuns = new AtomicInteger();
        AtomicInteger bRuns = new AtomicInteger();
        AtomicInteger cRuns = new AtomicInteger();
        AtomicInteger dRuns = new AtomicInteger();
        Pipeline pipeline = Pipeline.builder("replan")
                .stage("a", () -> "a" + aRuns.incrementAndGet())
                .stage("b", () -> "b" + bRuns.incrementAndGet(), "a")
                .stage("c", () -> "c" + cRuns.incrementAndGet(), "b")
                .stage("d", () -> "d" + dRuns.incrementAndGet(), "a")
                .build();
        RunReport first = executor.run(pipeline);

        RunReport second = executor.rerun(pipeline, first, "b");

        assertThat(second.succeeded()).isTrue();
        assertThat(List.of(aRuns.get(), bRuns.get(), cRuns.get(), dRuns.get())).containsExactly(1, 2, 2, 1);
        assertThat(second.outcome("a").reused()).isTrue();
        assertThat(second.outcome("d").reused()).isTrue();
        assertThat(second.outcome("c").detail()).isEqualTo("c2");
        assertThat(second.reusedCount()).isEqualTo(2);
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
            public void stageRetrying(Stage stage, int failedAttempt, int maxAttempts, String reason) {
                received.add("retrying:" + stage.id() + ":" + failedAttempt + "/" + maxAttempts);
            }

            @Override
            public void stageFinished(StageOutcome outcome) {
                received.add("finished:" + outcome.stageId() + ":" + outcome.status());
            }

            @Override
            public void safeStop(String reason) {
                received.add("safe-stop");
            }

            @Override
            public void runFinished(RunReport report) {
                received.add("run-finished");
            }
        };
        Pipeline pipeline = Pipeline.builder("events")
                .stage("a", () -> {
                    throw new IllegalStateException("boom");
                }, StageControls.defaults().withRetries(2, Duration.ZERO))
                .stage("b", () -> "ok", "a")
                .build();

        new DagExecutor(listener).run(pipeline);

        assertThat(received).containsExactly("run-started", "started:a", "retrying:a:1/2", "safe-stop",
                "finished:a:FAILED", "finished:b:SKIPPED", "run-finished");
    }
}
