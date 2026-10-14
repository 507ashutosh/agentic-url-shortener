package com.agentic.orchestrator.engine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs a pipeline. Each stage starts as soon as all its dependencies have finished, so
 * independent stages run in parallel and a stage with several dependencies waits for all
 * of them (the synchronization point).
 *
 * <p>Around every stage it applies the stage's controls: entry gates, bounded retries,
 * exit gates, and rollback of a failed attempt. When a stage finally fails or a gate stops it,
 * the run is safe-stopped: no new stage starts, stages already running finish.
 */
public final class DagExecutor {

    private final RunListener listener;

    public DagExecutor(RunListener listener) {
        this.listener = listener;
    }

    public RunReport run(Pipeline pipeline) {
        return execute(pipeline, Map.of());
    }

    /**
     * Re-planning: after {@code changedStage}'s inputs changed, runs it and everything downstream
     * of it again, and reuses the earlier results of every other stage that succeeded.
     */
    public RunReport rerun(Pipeline pipeline, RunReport previous, String changedStage) {
        Set<String> affected = new java.util.HashSet<>(pipeline.downstreamOf(changedStage));
        affected.add(changedStage);

        Map<String, StageOutcome> reused = new LinkedHashMap<>();
        List<String> rerun = new ArrayList<>();
        for (String id : pipeline.topologicalOrder()) {
            StageOutcome before = previous.outcome(id);
            if (!affected.contains(id) && before.status() == StageStatus.SUCCEEDED) {
                reused.put(id, before);
            } else {
                rerun.add(id);
            }
        }
        listener.replanning(changedStage, rerun, List.copyOf(reused.keySet()));
        return execute(pipeline, reused);
    }

    private RunReport execute(Pipeline pipeline, Map<String, StageOutcome> reused) {
        long runStart = System.nanoTime();
        AtomicReference<String> stopReason = new AtomicReference<>();
        listener.runStarted(pipeline);

        Map<String, CompletableFuture<StageOutcome>> futures = new HashMap<>();
        // One lightweight virtual thread per running stage.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Topological order guarantees a stage's dependencies already have futures.
            for (String id : pipeline.topologicalOrder()) {
                Stage stage = pipeline.stage(id);
                List<CompletableFuture<StageOutcome>> dependencies =
                        stage.dependsOn().stream().map(futures::get).toList();

                CompletableFuture<StageOutcome> future = CompletableFuture
                        .allOf(dependencies.toArray(CompletableFuture[]::new))   // wait for every dependency
                        .thenApplyAsync(ignored -> schedule(stage, dependencies, reused, stopReason, runStart),
                                executor);
                futures.put(id, future);
            }
            CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).join();
        }

        List<StageOutcome> outcomes = pipeline.topologicalOrder().stream()
                .map(id -> futures.get(id).join())
                .toList();
        RunReport report = new RunReport(pipeline.name(), outcomes, since(runStart), stopReason.get());
        listener.runFinished(report);
        return report;
    }

    /** Decides whether the stage is reused, skipped or run, once its dependencies are done. */
    private StageOutcome schedule(Stage stage, List<CompletableFuture<StageOutcome>> dependencies,
                                  Map<String, StageOutcome> reused, AtomicReference<String> stopReason,
                                  long runStart) {
        StageOutcome outcome;
        Optional<StageOutcome> blocker = dependencies.stream()
                .map(CompletableFuture::join)   // already complete; join just reads the value
                .filter(o -> o.status() != StageStatus.SUCCEEDED)
                .findFirst();
        if (reused.containsKey(stage.id())) {
            outcome = reused.get(stage.id()).asReused();
        } else if (stopReason.get() != null) {
            outcome = StageOutcome.skipped(stage.id(), "safe-stop: " + stopReason.get(), since(runStart));
        } else if (blocker.isPresent()) {
            StageOutcome b = blocker.get();
            outcome = StageOutcome.skipped(stage.id(),
                    "dependency '" + b.stageId() + "' " + b.status().name().toLowerCase(), since(runStart));
        } else {
            outcome = runGoverned(stage, stopReason, runStart);
        }
        listener.stageFinished(outcome);
        return outcome;
    }

    /** Entry gates, then up to maxAttempts tries, each followed by the exit gates. */
    private StageOutcome runGoverned(Stage stage, AtomicReference<String> stopReason, long runStart) {
        StageControls controls = stage.controls();
        Duration startedAt = since(runStart);

        for (Gate gate : controls.entryGates()) {
            GateDecision decision = check(gate, stage);
            if (!decision.passed()) {
                listener.gateFailed(stage, gate, decision);
                requestStop(stopReason, stage.id() + " blocked by " + gate.name() + ": " + decision.reason());
                return new StageOutcome(stage.id(), StageStatus.BLOCKED, decision.reason(), startedAt,
                        Duration.ZERO, 0, false);
            }
        }

        listener.stageStarted(stage, startedAt);
        long stageStart = System.nanoTime();
        String lastError = null;
        int attempt = 1;
        for (; attempt <= controls.maxAttempts(); attempt++) {
            try {
                String summary = stage.action().execute();
                GateDecision exit = checkExitGates(stage);
                if (exit.passed()) {
                    controls.rollback().commit(stage.id());
                    return new StageOutcome(stage.id(), StageStatus.SUCCEEDED, summary, startedAt,
                            since(stageStart), attempt, false);
                }
                rollback(stage);
                if (exit.verdict() == GateDecision.Verdict.STOP) {
                    requestStop(stopReason, stage.id() + ": " + exit.reason());
                    return new StageOutcome(stage.id(), StageStatus.BLOCKED, exit.reason(), startedAt,
                            since(stageStart), attempt, false);
                }
                lastError = exit.reason();
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                lastError = describe(e);
                rollback(stage);
            }
            if (attempt < controls.maxAttempts()) {
                listener.stageRetrying(stage, attempt, controls.maxAttempts(), lastError);
                pause(controls.retryBackoff());
            }
        }

        int attempts = controls.maxAttempts();
        requestStop(stopReason, stage.id() + " failed after " + attempts + " attempt" + (attempts == 1 ? "" : "s"));
        return new StageOutcome(stage.id(), StageStatus.FAILED, lastError, startedAt, since(stageStart),
                attempts, false);
    }

    private GateDecision checkExitGates(Stage stage) {
        for (Gate gate : stage.controls().exitGates()) {
            GateDecision decision = check(gate, stage);
            if (!decision.passed()) {
                listener.gateFailed(stage, gate, decision);
                return decision;
            }
        }
        return GateDecision.pass();
    }

    // A gate that throws is treated as a stop: when the check itself is broken, do not guess.
    private static GateDecision check(Gate gate, Stage stage) {
        try {
            return gate.check(stage);
        } catch (RuntimeException e) {
            return GateDecision.stop(gate.name() + " could not be evaluated: " + describe(e));
        }
    }

    private void rollback(Stage stage) {
        String undone = stage.controls().rollback().rollback(stage.id());
        if (undone != null) {
            listener.stageRolledBack(stage, undone);
        }
    }

    private void requestStop(AtomicReference<String> stopReason, String reason) {
        if (stopReason.compareAndSet(null, reason)) {
            listener.safeStop(reason);
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String describe(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static Duration since(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }
}
