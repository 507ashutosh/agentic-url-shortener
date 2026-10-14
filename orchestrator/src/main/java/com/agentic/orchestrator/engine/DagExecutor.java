package com.agentic.orchestrator.engine;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs a pipeline. Each stage starts as soon as all its dependencies have finished, so
 * independent stages run in parallel and a stage with several dependencies waits for all
 * of them (the synchronization point). If a dependency did not succeed, the stage is skipped.
 */
public final class DagExecutor {

    private final RunListener listener;

    public DagExecutor(RunListener listener) {
        this.listener = listener;
    }

    public RunReport run(Pipeline pipeline) {
        long runStart = System.nanoTime();
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
                        .thenApplyAsync(ignored -> runOrSkip(stage, dependencies, runStart), executor);
                futures.put(id, future);
            }
            CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).join();
        }

        List<StageOutcome> outcomes = pipeline.topologicalOrder().stream()
                .map(id -> futures.get(id).join())
                .toList();
        RunReport report = new RunReport(pipeline.name(), outcomes, since(runStart));
        listener.runFinished(report);
        return report;
    }

    private StageOutcome runOrSkip(Stage stage, List<CompletableFuture<StageOutcome>> dependencies, long runStart) {
        Optional<StageOutcome> blocker = dependencies.stream()
                .map(CompletableFuture::join)   // already complete; join just reads the value
                .filter(o -> o.status() != StageStatus.SUCCEEDED)
                .findFirst();
        StageOutcome outcome = blocker
                .map(b -> new StageOutcome(stage.id(), StageStatus.SKIPPED,
                        "dependency '" + b.stageId() + "' " + b.status().name().toLowerCase(),
                        since(runStart), Duration.ZERO))
                .orElseGet(() -> execute(stage, runStart));
        listener.stageFinished(outcome);
        return outcome;
    }

    private StageOutcome execute(Stage stage, long runStart) {
        Duration startedAt = since(runStart);
        listener.stageStarted(stage, startedAt);
        long stageStart = System.nanoTime();
        try {
            String summary = stage.action().execute();
            return new StageOutcome(stage.id(), StageStatus.SUCCEEDED, summary, startedAt, since(stageStart));
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new StageOutcome(stage.id(), StageStatus.FAILED, describe(e), startedAt, since(stageStart));
        }
    }

    private static String describe(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static Duration since(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }
}
