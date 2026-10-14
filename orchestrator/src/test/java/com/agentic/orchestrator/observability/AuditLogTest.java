package com.agentic.orchestrator.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.agentic.orchestrator.engine.CompositeRunListener;
import com.agentic.orchestrator.engine.DagExecutor;
import com.agentic.orchestrator.engine.Gate;
import com.agentic.orchestrator.engine.GateDecision;
import com.agentic.orchestrator.engine.Pipeline;
import com.agentic.orchestrator.engine.RollbackHandler;
import com.agentic.orchestrator.engine.RunListener;
import com.agentic.orchestrator.engine.RunReport;
import com.agentic.orchestrator.engine.StageControls;

class AuditLogTest {

    @TempDir
    Path dir;

    private static final RollbackHandler UNDO = new RollbackHandler() {
        @Override
        public void commit(String stageId) {
        }

        @Override
        public String rollback(String stageId) {
            return "1 file reverted";
        }
    };

    /** a → b (fails once, then succeeds) → c (always fails). */
    private RunReport runWithOneRecoveryAndOneFailure(AuditLog audit) {
        AtomicInteger bCalls = new AtomicInteger();
        Pipeline pipeline = Pipeline.builder("audited")
                .stage("a", () -> "ok")
                .stage("b", () -> {
                    if (bCalls.incrementAndGet() == 1) {
                        throw new IllegalStateException("transient");
                    }
                    return "ok";
                }, StageControls.defaults().withRetries(3, Duration.ZERO).withRollback(UNDO), "a")
                .stage("c", () -> "ok", StageControls.defaults()
                        .withExitGate(Gate.of("policy", s -> GateDecision.retry("secret found"))), "b")
                .build();
        return new DagExecutor(new CompositeRunListener(new RunListener() {
        }, audit)).run(pipeline);
    }

    @Test
    void writesOneLinePerEventAndTheHashChainVerifies() throws IOException {
        AuditLog audit = new AuditLog(dir.resolve("audit.jsonl"), "run-1");

        runWithOneRecoveryAndOneFailure(audit);

        List<String> lines = Files.readAllLines(audit.file());
        assertThat(lines).isNotEmpty().allMatch(l -> l.contains("\"runId\":\"run-1\""));
        assertThat(lines.get(0)).contains("\"type\":\"run.started\"").contains("\"prevHash\":\"" + AuditLog.GENESIS);
        assertThat(lines).anyMatch(l -> l.contains("\"stage.retrying\"") && l.contains("transient"));
        assertThat(lines).anyMatch(l -> l.contains("\"gate.failed\"") && l.contains("secret found"));
        assertThat(AuditLog.verify(audit.file())).isEqualTo(-1);
    }

    @Test
    void verifyFindsTheFirstChangedOrRemovedEvent() throws IOException {
        AuditLog audit = new AuditLog(dir.resolve("audit.jsonl"), "run-1");
        runWithOneRecoveryAndOneFailure(audit);
        List<String> lines = new ArrayList<>(Files.readAllLines(audit.file()));

        Path edited = dir.resolve("edited.jsonl");
        List<String> changed = new ArrayList<>(lines);
        changed.set(3, changed.get(3).replace("\"stage\":", "\"stage\":\"x\",\"was\":"));
        Files.write(edited, changed);
        assertThat(AuditLog.verify(edited)).isEqualTo(4);

        Path deleted = dir.resolve("deleted.jsonl");
        List<String> shorter = new ArrayList<>(lines);
        shorter.remove(2);
        Files.write(deleted, shorter);
        assertThat(AuditLog.verify(deleted)).isEqualTo(4);   // event 4 no longer points at event 3
    }

    @Test
    void completeSummarisesRetriesRollbacksRecoveryAndHumanWait() {
        AuditLog audit = new AuditLog(dir.resolve("audit.jsonl"), "run-1");
        RunReport report = runWithOneRecoveryAndOneFailure(audit);
        audit.record("approval", "c", AuditSink.data("outcome", "APPROVED", "waitedMs", 1500L));
        audit.record("llm.fallback", "b", AuditSink.data("reason", "outage"));

        RunMetrics metrics = audit.complete(report, "test", "scripted");

        assertThat(metrics.outcome()).isEqualTo("STOPPED");
        assertThat(metrics.retries()).isEqualTo(1);
        assertThat(metrics.rollbacks()).isEqualTo(1);           // b's failed attempt
        assertThat(metrics.recoveryTimesMs()).hasSize(1);      // b recovered
        assertThat(metrics.unrecoveredFailures()).isEqualTo(1); // c never did
        assertThat(metrics.humanWaitMs()).isEqualTo(1500);
        assertThat(metrics.agentTimeMs()).isEqualTo(Math.max(0, metrics.wallClockMs() - 1500));
        assertThat(metrics.fallbacks()).isEqualTo(1);
        assertThat(metrics.approvalsGranted()).isEqualTo(1);
        assertThat(metrics.stageExecutions()).isEqualTo(3);
        assertThat(metrics.stages()).extracting(RunMetrics.StageMetric::status)
                .containsExactly("SUCCEEDED", "SUCCEEDED", "FAILED");
    }
}
