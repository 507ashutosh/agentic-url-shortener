package com.agentic.orchestrator.observability;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

/**
 * Reliability metrics across runs: reads every runs/&lt;run-id&gt;/run-summary.json, aggregates them,
 * prints a report and saves it as runs/metrics.json.
 *
 * <ul>
 *   <li>success rate: runs that finished OK / all runs</li>
 *   <li>retry frequency: retries / stage executions</li>
 *   <li>rollback frequency: rollbacks per run, and share of runs with at least one</li>
 *   <li>MTTR: mean time from a stage's first failed attempt to its eventual success</li>
 *   <li>end-to-end latency: wall clock per run, and agent time excluding human approval wait</li>
 * </ul>
 */
public final class MetricsReport {

    public static final String SUMMARY_FILE = "run-summary.json";

    /** The aggregated numbers, also written to runs/metrics.json. */
    public record Aggregate(
            int runs,
            int succeeded,
            double successRate,
            int retries,
            int stageExecutions,
            double retryRate,
            int rollbacks,
            double runsWithRollbackRate,
            int fallbacks,
            int policyBlocks,
            int recoveries,
            int unrecoveredFailures,
            Long mttrMs,
            long latencyP50Ms,
            long latencyP95Ms,
            long latencyMaxMs,
            long agentTimeP50Ms,
            long agentTimeP95Ms,
            long totalHumanWaitMs,
            Map<String, Map<String, Integer>> outcomesByScenario,
            Map<String, Integer> failuresByStage) {
    }

    private MetricsReport() {
    }

    public static void save(RunMetrics metrics, Path runDir) {
        try {
            Json.PRETTY.writeValue(runDir.resolve(SUMMARY_FILE).toFile(), metrics);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<RunMetrics> loadAll(Path runsDir) {
        if (!Files.isDirectory(runsDir)) {
            return List.of();
        }
        List<RunMetrics> all = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(runsDir)) {
            for (Path summary : dirs.map(d -> d.resolve(SUMMARY_FILE)).filter(Files::isRegularFile).toList()) {
                all.add(Json.LINE.readValue(summary.toFile(), RunMetrics.class));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        all.sort(Comparator.comparing(RunMetrics::startedAt));
        return all;
    }

    public static Aggregate aggregate(List<RunMetrics> runs) {
        int succeeded = (int) runs.stream().filter(RunMetrics::succeeded).count();
        int retries = sum(runs, RunMetrics::retries);
        int executions = sum(runs, RunMetrics::stageExecutions);
        int rollbacks = sum(runs, RunMetrics::rollbacks);
        List<Long> recoveries = runs.stream().flatMap(r -> r.recoveryTimesMs().stream()).toList();

        Map<String, Map<String, Integer>> byScenario = new LinkedHashMap<>();
        Map<String, Integer> failuresByStage = new LinkedHashMap<>();
        for (RunMetrics run : runs) {
            byScenario.computeIfAbsent(run.scenario(), k -> new LinkedHashMap<>()).merge(run.outcome(), 1, Integer::sum);
            run.stages().stream().filter(s -> s.status().equals("FAILED") || s.status().equals("BLOCKED"))
                    .forEach(s -> failuresByStage.merge(s.stageId(), 1, Integer::sum));
        }

        return new Aggregate(runs.size(), succeeded, ratio(succeeded, runs.size()),
                retries, executions, ratio(retries, executions),
                rollbacks, ratio((int) runs.stream().filter(r -> r.rollbacks() > 0).count(), runs.size()),
                sum(runs, RunMetrics::fallbacks), sum(runs, RunMetrics::policyBlocks),
                recoveries.size(), sum(runs, RunMetrics::unrecoveredFailures),
                recoveries.isEmpty() ? null : Math.round(recoveries.stream().mapToLong(Long::longValue).average()
                        .orElse(0)),
                percentile(runs, RunMetrics::wallClockMs, 50), percentile(runs, RunMetrics::wallClockMs, 95),
                percentile(runs, RunMetrics::wallClockMs, 100),
                percentile(runs, RunMetrics::agentTimeMs, 50), percentile(runs, RunMetrics::agentTimeMs, 95),
                runs.stream().mapToLong(RunMetrics::humanWaitMs).sum(),
                byScenario, failuresByStage);
    }

    /** Aggregates every run under runsDir, prints the report and writes runs/metrics.json. */
    public static Aggregate printAndSave(Path runsDir, PrintStream out) {
        List<RunMetrics> runs = loadAll(runsDir);
        if (runs.isEmpty()) {
            out.println("No runs with a " + SUMMARY_FILE + " found under " + runsDir);
            return null;
        }
        Aggregate a = aggregate(runs);
        out.printf("Reliability metrics across %d run%s (%s)%n%n", a.runs(), a.runs() == 1 ? "" : "s", runsDir);
        out.printf("  success rate         %d/%d  %s%n", a.succeeded(), a.runs(), percent(a.successRate()));
        out.printf("  retry frequency      %d retries / %d stage executions  %s%n", a.retries(),
                a.stageExecutions(), percent(a.retryRate()));
        out.printf("  rollback frequency   %d rollbacks · %s of runs had one%n", a.rollbacks(),
                percent(a.runsWithRollbackRate()));
        out.printf("  fallbacks            %d · policy blocks %d%n", a.fallbacks(), a.policyBlocks());
        out.printf("  MTTR                 %s over %d recovered failure%s · %d not recovered%n",
                a.mttrMs() == null ? "n/a" : seconds(a.mttrMs()), a.recoveries(),
                a.recoveries() == 1 ? "" : "s", a.unrecoveredFailures());
        out.printf("  end-to-end latency   p50 %s · p95 %s · max %s%n", seconds(a.latencyP50Ms()),
                seconds(a.latencyP95Ms()), seconds(a.latencyMaxMs()));
        out.printf("  agent time           p50 %s · p95 %s  (excludes %s waiting for approvals)%n",
                seconds(a.agentTimeP50Ms()), seconds(a.agentTimeP95Ms()), seconds(a.totalHumanWaitMs()));
        out.printf("%n  outcomes by scenario %s%n", a.outcomesByScenario());
        out.printf("  failed/blocked stages %s%n", a.failuresByStage().isEmpty() ? "none" : a.failuresByStage());

        try {
            Json.PRETTY.writeValue(runsDir.resolve("metrics.json").toFile(), a);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.printf("%nSaved to %s%n", runsDir.resolve("metrics.json"));
        return a;
    }

    private static int sum(List<RunMetrics> runs, java.util.function.ToIntFunction<RunMetrics> field) {
        return runs.stream().mapToInt(field).sum();
    }

    private static double ratio(int part, int whole) {
        return whole == 0 ? 0 : (double) part / whole;
    }

    // Nearest-rank percentile; with few runs this is simply one of the observed values.
    private static long percentile(List<RunMetrics> runs, ToLongFunction<RunMetrics> field, int p) {
        long[] values = runs.stream().mapToLong(field).sorted().toArray();
        if (values.length == 0) {
            return 0;
        }
        int rank = (int) Math.ceil(p / 100.0 * values.length);
        return values[Math.max(0, Math.min(values.length - 1, rank - 1))];
    }

    private static String percent(double ratio) {
        return String.format(Locale.ROOT, "%.0f%%", ratio * 100);
    }

    private static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.1fs", millis / 1000.0);
    }
}
