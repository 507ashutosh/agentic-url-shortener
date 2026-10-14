package com.agentic.orchestrator.console;

import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import com.agentic.orchestrator.engine.Pipeline;
import com.agentic.orchestrator.engine.RunListener;
import com.agentic.orchestrator.engine.RunReport;
import com.agentic.orchestrator.engine.Stage;
import com.agentic.orchestrator.engine.StageOutcome;
import com.agentic.orchestrator.engine.StageStatus;

/** Prints the plan and each stage event to the terminal, with time since the run started. */
public class ConsoleRunListener implements RunListener {

    private final PrintStream out;

    public ConsoleRunListener(PrintStream out) {
        this.out = out;
    }

    @Override
    public synchronized void runStarted(Pipeline pipeline) {
        List<List<String>> waves = pipeline.waves();
        out.printf("Pipeline '%s' · %d stages · %d waves%n", pipeline.name(), pipeline.size(), waves.size());
        for (int i = 0; i < waves.size(); i++) {
            String note = waves.get(i).size() > 1 ? "   (parallel)" : "";
            out.printf("  wave %d: %s%s%n", i + 1, String.join(", ", waves.get(i)), note);
        }
        out.println();
    }

    @Override
    public synchronized void stageStarted(Stage stage, Duration startedAt) {
        out.printf("  %s  ▶ %s%n", clock(startedAt), stage.id());
    }

    @Override
    public synchronized void stageFinished(StageOutcome outcome) {
        Duration finishedAt = outcome.startedAt().plus(outcome.duration());
        out.printf("  %s  %s %-16s %s%n", clock(finishedAt), symbol(outcome.status()), outcome.stageId(),
                outcome.status() == StageStatus.SKIPPED
                        ? "skipped: " + outcome.detail()
                        : seconds(outcome.duration()) + "  " + outcome.detail());
    }

    @Override
    public synchronized void runFinished(RunReport report) {
        out.println();
        out.printf("Run %s · %d succeeded, %d failed, %d skipped%n",
                report.succeeded() ? "OK" : "FAILED",
                report.count(StageStatus.SUCCEEDED), report.count(StageStatus.FAILED),
                report.count(StageStatus.SKIPPED));
        out.printf("Wall clock %s · sum of stage times %s (the difference is what parallelism saved)%n",
                seconds(report.wallClock()), seconds(report.sumOfStageDurations()));
    }

    private static String symbol(StageStatus status) {
        return switch (status) {
            case SUCCEEDED -> "✓";
            case FAILED -> "✗";
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
