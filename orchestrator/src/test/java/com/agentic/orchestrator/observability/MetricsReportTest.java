package com.agentic.orchestrator.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.agentic.orchestrator.observability.RunMetrics.StageMetric;

class MetricsReportTest {

    @TempDir
    Path runsDir;

    private static RunMetrics run(String id, String outcome, long wallMs, long waitMs, int retries, int rollbacks,
                                  List<Long> recoveries, int unrecovered, String failedStage) {
        List<StageMetric> stages = failedStage == null
                ? List.of(new StageMetric("design", "SUCCEEDED", 1, 10, false))
                : List.of(new StageMetric(failedStage, "FAILED", 1, 10, false));
        return new RunMetrics(id, "brownfield", "scripted", "2026-10-14T10:00:0" + id.charAt(id.length() - 1) + "Z",
                outcome, null, wallMs, waitMs, wallMs - waitMs, 9, 0, 0, 0, 0, 10, retries, rollbacks, 0, 0,
                0, 0, 0, 0, recoveries, unrecovered, stages);
    }

    @Test
    void aggregatesSuccessRetriesRollbacksMttrAndLatency() {
        List<RunMetrics> runs = List.of(
                run("r1", "OK", 6_000, 0, 0, 0, List.of(), 0, null),
                run("r2", "OK", 7_000, 1_000, 2, 1, List.of(400L, 800L), 0, null),
                run("r3", "STOPPED", 2_000, 0, 0, 1, List.of(), 1, "validate"),
                run("r4", "OK", 20_000, 14_000, 1, 0, List.of(600L), 0, null));

        MetricsReport.Aggregate a = MetricsReport.aggregate(runs);

        assertThat(a.runs()).isEqualTo(4);
        assertThat(a.successRate()).isEqualTo(0.75);
        assertThat(a.retries()).isEqualTo(3);
        assertThat(a.retryRate()).isEqualTo(3 / 40.0);
        assertThat(a.rollbacks()).isEqualTo(2);
        assertThat(a.runsWithRollbackRate()).isEqualTo(0.5);
        assertThat(a.mttrMs()).isEqualTo(600);                 // mean of 400, 800, 600
        assertThat(a.recoveries()).isEqualTo(3);
        assertThat(a.unrecoveredFailures()).isEqualTo(1);
        assertThat(a.latencyP50Ms()).isEqualTo(6_000);
        assertThat(a.latencyMaxMs()).isEqualTo(20_000);
        assertThat(a.agentTimeP95Ms()).isEqualTo(6_000);       // r4's 20s was mostly human wait
        assertThat(a.failuresByStage()).containsEntry("validate", 1);
        assertThat(a.outcomesByScenario().get("brownfield")).containsEntry("OK", 3).containsEntry("STOPPED", 1);
    }

    @Test
    void savesRunSummariesAndReadsThemBack() throws Exception {
        for (String id : List.of("r1", "r2")) {
            Path runDir = Files.createDirectories(runsDir.resolve(id));
            MetricsReport.save(run(id, "OK", 5_000, 0, 0, 0, List.of(), 0, null), runDir);
        }
        Files.createDirectories(runsDir.resolve("old-run-without-summary"));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        MetricsReport.printAndSave(runsDir, new PrintStream(buffer, true));

        assertThat(MetricsReport.loadAll(runsDir)).extracting(RunMetrics::runId).containsExactly("r1", "r2");
        assertThat(buffer.toString()).contains("success rate         2/2  100%").contains("MTTR                 n/a");
        assertThat(runsDir.resolve("metrics.json")).exists();
    }
}
