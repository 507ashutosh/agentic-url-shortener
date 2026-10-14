package com.agentic.orchestrator.observability;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.agentic.orchestrator.engine.Gate;
import com.agentic.orchestrator.engine.GateDecision;
import com.agentic.orchestrator.engine.Pipeline;
import com.agentic.orchestrator.engine.RunListener;
import com.agentic.orchestrator.engine.RunReport;
import com.agentic.orchestrator.engine.Stage;
import com.agentic.orchestrator.engine.StageOutcome;
import com.agentic.orchestrator.engine.StageStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The run's audit trail: every engine and governance event, appended as one JSON line to
 * runs/&lt;run-id&gt;/audit.jsonl the moment it happens, so nothing is lost if the run crashes.
 *
 * <p>Each line carries the SHA-256 hash of the previous line, forming a hash chain:
 * changing or deleting any past event breaks every hash after it, and {@link #verify} detects it.
 *
 * <p>While recording, it also counts what the metrics need (retries, rollbacks, recovery times, ...).
 */
public final class AuditLog implements RunListener, AuditSink {

    static final String GENESIS = "0".repeat(64);

    private final Path file;
    private final String runId;
    private final Instant startedAt = Instant.now();
    private final long startNanos = System.nanoTime();
    private long sequence;
    private String previousHash = GENESIS;

    // Counters for the metrics, updated as events are recorded.
    private int retries;
    private int rollbacks;
    private int fallbacks;
    private int policyBlocks;
    private int approvalsGranted;
    private int approvalsRejected;
    private int revisions;
    private int replans;
    private long humanWaitMs;
    private int stageExecutions;
    private int unrecovered;
    private final List<Long> recoveryTimesMs = new ArrayList<>();
    private final Map<String, Long> firstFailureNanos = new HashMap<>();

    public AuditLog(Path file, String runId) {
        this.file = file;
        this.runId = runId;
    }

    public Path file() {
        return file;
    }

    // ---------------------------------------------------------------- recording

    @Override
    public synchronized void record(String type, String stageId, Map<String, Object> data) {
        count(type, stageId, data);

        ObjectNode event = Json.LINE.createObjectNode();
        event.put("seq", ++sequence);
        event.put("at", Instant.now().toString());
        event.put("runId", runId);
        event.put("type", type);
        event.put("stage", stageId);
        event.set("data", Json.LINE.valueToTree(data));
        event.put("prevHash", previousHash);
        String hash = sha256(previousHash + canonical(event));
        event.put("hash", hash);
        previousHash = hash;

        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(Json.LINE.writeValueAsString(event));
            writer.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write audit log " + file, e);
        }
    }

    private void count(String type, String stageId, Map<String, Object> data) {
        switch (type) {
            case "stage.started" -> stageExecutions++;
            case "stage.retrying" -> {
                retries++;
                firstFailureNanos.putIfAbsent(stageId, System.nanoTime());
            }
            case "stage.rolled_back" -> rollbacks++;
            case "llm.fallback" -> fallbacks++;
            case "replanning" -> replans++;
            case "policy.finding" -> {
                if ("BLOCK".equals(data.get("severity"))) {
                    policyBlocks++;
                }
            }
            case "approval" -> {
                humanWaitMs += ((Number) data.get("waitedMs")).longValue();
                switch (String.valueOf(data.get("outcome"))) {
                    case "APPROVED" -> approvalsGranted++;
                    case "REJECTED" -> approvalsRejected++;
                    case "REVISE" -> revisions++;
                    default -> {
                    }
                }
            }
            case "stage.finished" -> {
                // A stage that failed earlier and has now succeeded has recovered: that time feeds MTTR.
                String status = String.valueOf(data.get("status"));
                Long failedAt = firstFailureNanos.remove(stageId);
                if ("SUCCEEDED".equals(status) && failedAt != null && !Boolean.TRUE.equals(data.get("reused"))) {
                    recoveryTimesMs.add(Duration.ofNanos(System.nanoTime() - failedAt).toMillis());
                } else if ("FAILED".equals(status)) {
                    unrecovered++;
                }
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- engine events

    @Override
    public void runStarted(Pipeline pipeline) {
        record("run.started", null, AuditSink.data("pipeline", pipeline.name(), "stages", pipeline.topologicalOrder(),
                "waves", pipeline.waves()));
    }

    @Override
    public void replanning(String changedStage, List<String> rerun, List<String> reused) {
        record("replanning", changedStage, AuditSink.data("rerun", rerun, "reused", reused));
    }

    @Override
    public void stageStarted(Stage stage, Duration startedAt) {
        record("stage.started", stage.id(), AuditSink.data("dependsOn", stage.dependsOn(),
                "maxAttempts", stage.controls().maxAttempts()));
    }

    @Override
    public void gateFailed(Stage stage, Gate gate, GateDecision decision) {
        record("gate.failed", stage.id(), AuditSink.data("gate", gate.name(), "verdict", decision.verdict().name(),
                "reason", decision.reason()));
    }

    @Override
    public void stageRetrying(Stage stage, int failedAttempt, int maxAttempts, String reason) {
        record("stage.retrying", stage.id(), AuditSink.data("failedAttempt", failedAttempt, "maxAttempts", maxAttempts,
                "reason", reason));
    }

    @Override
    public void stageRolledBack(Stage stage, String whatWasUndone) {
        record("stage.rolled_back", stage.id(), AuditSink.data("undone", whatWasUndone));
    }

    @Override
    public void stageFinished(StageOutcome outcome) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", outcome.status().name());
        data.put("attempts", outcome.attempts());
        data.put("durationMs", outcome.duration().toMillis());
        data.put("reused", outcome.reused());
        data.put("detail", outcome.detail());
        record("stage.finished", outcome.stageId(), data);
    }

    @Override
    public void safeStop(String reason) {
        record("safe_stop", null, AuditSink.data("reason", reason));
    }

    @Override
    public void runFinished(RunReport report) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("succeeded", report.succeeded());
        data.put("wallClockMs", report.wallClock().toMillis());
        data.put("stopReason", report.stopReason());
        record("pipeline.finished", null, data);
    }

    // ---------------------------------------------------------------- summary

    /** Records the final event and returns this run's metrics. Call once, after the last pipeline pass. */
    public synchronized RunMetrics complete(RunReport finalReport, String scenario, String agents) {
        long wallClockMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        List<RunMetrics.StageMetric> stages = finalReport.outcomes().stream()
                .map(o -> new RunMetrics.StageMetric(o.stageId(), o.status().name(), o.attempts(),
                        o.duration().toMillis(), o.reused()))
                .toList();
        RunMetrics metrics = new RunMetrics(runId, scenario, agents, startedAt.toString(),
                finalReport.succeeded() ? "OK" : "STOPPED", finalReport.stopReason(),
                wallClockMs, humanWaitMs, Math.max(0, wallClockMs - humanWaitMs),
                (int) finalReport.count(StageStatus.SUCCEEDED), (int) finalReport.count(StageStatus.FAILED),
                (int) finalReport.count(StageStatus.BLOCKED), (int) finalReport.count(StageStatus.SKIPPED),
                (int) finalReport.reusedCount(), stageExecutions, retries, rollbacks, fallbacks, policyBlocks,
                approvalsGranted, approvalsRejected, revisions, replans, List.copyOf(recoveryTimesMs),
                unrecovered, stages);
        record("run.completed", null, AuditSink.data("outcome", metrics.outcome(), "wallClockMs", wallClockMs,
                "humanWaitMs", humanWaitMs, "retries", retries, "rollbacks", rollbacks));
        return metrics;
    }

    // ---------------------------------------------------------------- verification

    /**
     * Re-computes the hash chain of an audit file.
     *
     * @return the sequence number of the first event that does not match, or -1 if the file is intact
     */
    public static long verify(Path auditFile) {
        String previous = GENESIS;
        try {
            for (String line : Files.readAllLines(auditFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                ObjectNode event = (ObjectNode) Json.LINE.readTree(line);
                long seq = event.path("seq").asLong();
                String storedHash = event.path("hash").asText();
                if (!previous.equals(event.path("prevHash").asText())) {
                    return seq;
                }
                event.remove("hash");
                if (!sha256(previous + canonical(event)).equals(storedHash)) {
                    return seq;
                }
                previous = storedHash;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return -1;
    }

    // The exact bytes that are hashed: the event as compact JSON, without its own hash.
    private static String canonical(JsonNode event) {
        ObjectNode copy = ((ObjectNode) event).deepCopy();
        copy.remove("hash");
        try {
            return Json.LINE.writeValueAsString(copy);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
