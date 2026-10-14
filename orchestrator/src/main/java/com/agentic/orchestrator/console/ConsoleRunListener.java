package com.agentic.orchestrator.console;

import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import com.agentic.orchestrator.engine.Gate;
import com.agentic.orchestrator.engine.GateDecision;
import com.agentic.orchestrator.engine.Pipeline;
import com.agentic.orchestrator.engine.RunListener;
import com.agentic.orchestrator.engine.RunReport;
import com.agentic.orchestrator.engine.Stage;
import com.agentic.orchestrator.engine.StageOutcome;
import com.agentic.orchestrator.engine.StageStatus;

/**
 * Prints the plan and each stage event to the terminal, with time since the run started.
 * Every print locks the PrintStream, the same lock the approval prompt holds while it waits.
 */
public class ConsoleRunListener implements RunListener {

    private final PrintStream out;
    private final AtomicInteger rollbacks = new AtomicInteger();
    private volatile long runStart = System.nanoTime();

    public ConsoleRunListener(PrintStream out) {
        this.out = out;
    }

    @Override
    public void runStarted(Pipeline pipeline) {
        runStart = System.nanoTime();
        rollbacks.set(0);
        List<List<String>> waves = pipeline.waves();
        synchronized (out) {
            out.printf("Pipeline '%s' · %d stages · %d waves%n", pipeline.name(), pipeline.size(), waves.size());
            for (int i = 0; i < waves.size(); i++) {
                String note = waves.get(i).size() > 1 ? "   (parallel)" : "";
                out.printf("  wave %d: %s%s%n", i + 1, String.join(", ", waves.get(i)), note);
            }
            out.println();
        }
    }

    @Override
    public void replanning(String changedStage, List<String> rerun, List<String> reused) {
        synchronized (out) {
            out.printf("%nRe-planning after a change to '%s'%n", changedStage);
            out.printf("  re-run: %s%n", String.join(", ", rerun));
            out.printf("  reuse:  %s%n%n", reused.isEmpty() ? "(nothing)" : String.join(", ", reused));
        }
    }

    @Override
    public void stageStarted(Stage stage, Duration startedAt) {
        line(startedAt, "▶ " + stage.id());
    }

    @Override
    public void gateFailed(Stage stage, Gate gate, GateDecision decision) {
        line(now(), "⛔ %-16s %s: %s".formatted(stage.id(), gate.name(), decision.reason()));
    }

    @Override
    public void stageRetrying(Stage stage, int failedAttempt, int maxAttempts, String reason) {
        line(now(), "↻ %-16s attempt %d/%d failed: %s · retrying".formatted(stage.id(), failedAttempt, maxAttempts,
                reason));
    }

    @Override
    public void stageRolledBack(Stage stage, String whatWasUndone) {
        rollbacks.incrementAndGet();
        line(now(), "↺ %-16s rolled back: %s".formatted(stage.id(), whatWasUndone));
    }

    /** Free-form progress note, e.g. a switch to the fallback model. */
    public void note(String stageId, String message) {
        line(now(), "⤳ %-16s %s".formatted(stageId, message));
    }

    @Override
    public void stageFinished(StageOutcome outcome) {
        if (outcome.reused()) {
            line(Duration.ZERO, "= %-16s reused from the previous run".formatted(outcome.stageId()));
            return;
        }
        Duration finishedAt = outcome.startedAt().plus(outcome.duration());
        String text = switch (outcome.status()) {
            case SKIPPED -> "skipped: " + outcome.detail();
            case BLOCKED -> "blocked: " + outcome.detail();
            default -> seconds(outcome.duration()) + "  " + outcome.detail()
                    + (outcome.retries() > 0 ? "  (attempt " + outcome.attempts() + ")" : "");
        };
        line(finishedAt, "%s %-16s %s".formatted(symbol(outcome.status()), outcome.stageId(), text));
    }

    @Override
    public void safeStop(String reason) {
        line(now(), "■ safe-stop: " + reason + " · no new stages will start");
    }

    @Override
    public void runFinished(RunReport report) {
        synchronized (out) {
            out.println();
            out.printf("Run %s · %d succeeded, %d failed, %d blocked, %d skipped%s%n",
                    report.succeeded() ? "OK" : "STOPPED",
                    report.count(StageStatus.SUCCEEDED), report.count(StageStatus.FAILED),
                    report.count(StageStatus.BLOCKED), report.count(StageStatus.SKIPPED),
                    report.reusedCount() > 0 ? " (" + report.reusedCount() + " reused)" : "");
            out.printf("%d retr%s · %d rollback%s · wall clock %s · sum of stage times %s%n",
                    report.retries(), report.retries() == 1 ? "y" : "ies",
                    rollbacks.get(), rollbacks.get() == 1 ? "" : "s",
                    seconds(report.wallClock()), seconds(report.sumOfStageDurations()));
            report.safeStop().ifPresent(reason -> out.printf("Stopped because: %s%n", reason));
        }
    }

    private void line(Duration at, String text) {
        synchronized (out) {
            out.printf("  %s  %s%n", clock(at), text);
        }
    }

    private Duration now() {
        return Duration.ofNanos(System.nanoTime() - runStart);
    }

    private static String symbol(StageStatus status) {
        return switch (status) {
            case SUCCEEDED -> "✓";
            case FAILED -> "✗";
            case BLOCKED -> "⛔";
            case SKIPPED -> "–";
        };
    }

    private static String clock(Duration sinceStart) {
        return String.format(Locale.ROOT, "[+%5.2fs]", sinceStart.toMillis() / 1000.0);
    }

    private static String seconds(Duration duration) {
        return String.format(Locale.ROOT, "%.2fs", duration.toMillis() / 1000.0);
    }
}
