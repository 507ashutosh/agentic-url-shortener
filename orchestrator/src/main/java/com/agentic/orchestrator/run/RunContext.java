package com.agentic.orchestrator.run;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.agentic.orchestrator.governance.approval.ApprovalDecision;
import com.agentic.orchestrator.governance.approval.ApprovalRequest;
import com.agentic.orchestrator.governance.policy.PolicyFinding;
import com.agentic.orchestrator.observability.AuditSink;
import com.agentic.orchestrator.scenario.Scenario;

/**
 * State shared by all stages of one run: the requirement, the workspace, every artifact
 * produced so far, the decision lineage, and the governance record (policy findings,
 * approvals, reviewer feedback). Thread-safe, because parallel stages write to it.
 */
public final class RunContext {

    /** A human's approval answer, what they were asked, and how long the run waited for them. */
    public record ApprovalRecord(ApprovalRequest request, ApprovalDecision decision, Duration waited) {
    }

    public record FindingRecord(String stageId, PolicyFinding finding) {
    }

    /** A reviewer asked for one stage to be redone with this feedback. */
    public record Revision(String stageId, String feedback) {
    }

    private final Scenario scenario;
    private final RunWorkspace workspace;
    private final AuditSink audit;
    private final Map<String, Artifact> artifacts = new ConcurrentHashMap<>();
    private final List<Decision> decisions = Collections.synchronizedList(new ArrayList<>());
    private final List<ApprovalRecord> approvals = Collections.synchronizedList(new ArrayList<>());
    private final List<FindingRecord> findings = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, List<String>> feedback = new ConcurrentHashMap<>();
    private final AtomicReference<Revision> pendingRevision = new AtomicReference<>();

    public RunContext(Scenario scenario, RunWorkspace workspace) {
        this(scenario, workspace, AuditSink.NONE);
    }

    /** @param audit receives every artifact, decision, policy finding and approval as it is recorded */
    public RunContext(Scenario scenario, RunWorkspace workspace, AuditSink audit) {
        this.scenario = scenario;
        this.workspace = workspace;
        this.audit = audit;
    }

    public Scenario scenario() {
        return scenario;
    }

    public RunWorkspace workspace() {
        return workspace;
    }

    /** Stores a stage's output and its decisions, and saves the report to the run folder. */
    public void record(Artifact artifact, List<Decision> stageDecisions) {
        artifacts.put(artifact.stageId(), artifact);
        decisions.removeIf(d -> d.stageId().equals(artifact.stageId()));   // a re-run replaces earlier decisions
        decisions.addAll(stageDecisions);
        workspace.saveArtifact(artifact, stageDecisions);
        audit.record("artifact", artifact.stageId(), AuditSink.data("summary", artifact.summary(),
                "filesWritten", artifact.filesWritten()));
        stageDecisions.forEach(d -> audit.record("decision", d.stageId(), AuditSink.data("kind", d.kind().name(),
                "text", d.text(), "rationale", d.rationale(), "basedOn", d.basedOn())));
    }

    public Optional<Artifact> artifact(String stageId) {
        return Optional.ofNullable(artifacts.get(stageId));
    }

    public List<Decision> decisions() {
        return snapshot(decisions);
    }

    public void recordPolicyFinding(String stageId, PolicyFinding finding) {
        findings.add(new FindingRecord(stageId, finding));
        audit.record("policy.finding", stageId, AuditSink.data("policy", finding.policyId(),
                "severity", finding.severity().name(), "reason", finding.reason(), "evidence", finding.evidence()));
    }

    public List<FindingRecord> policyFindings() {
        return snapshot(findings);
    }

    public void recordApproval(ApprovalRequest request, ApprovalDecision decision, Duration waited) {
        approvals.add(new ApprovalRecord(request, decision, waited));
        audit.record("approval", request.stageId(), AuditSink.data("policy", request.policyId(),
                "question", request.title(), "outcome", decision.outcome().name(), "approver", decision.approver(),
                "decidedAt", decision.decidedAt().toString(), "waitedMs", waited.toMillis(),
                "reviseStage", decision.reviseStage(), "feedback", decision.feedback()));
    }

    public List<ApprovalRecord> approvals() {
        return snapshot(approvals);
    }

    /** Called by the release gate when a reviewer sends a stage back: re-planning follows the run. */
    public void requestRevision(String stageId, String text) {
        pendingRevision.set(new Revision(stageId, text));
        addFeedback(stageId, text);
    }

    /** Human input for a stage (answers, review comments); the agent includes it in its next prompt. */
    public void addFeedback(String stageId, String text) {
        feedback.computeIfAbsent(stageId, k -> Collections.synchronizedList(new ArrayList<>())).add(text);
        audit.record("feedback", stageId, AuditSink.data("text", text));
    }

    /** Returns and clears the pending revision, if any. */
    public Optional<Revision> takeRevision() {
        return Optional.ofNullable(pendingRevision.getAndSet(null));
    }

    /** Reviewer feedback for a stage, oldest first; agents include it in their prompt. */
    public List<String> feedbackFor(String stageId) {
        return snapshot(feedback.getOrDefault(stageId, List.of()));
    }

    private static <T> List<T> snapshot(List<T> list) {
        synchronized (list) {
            return List.copyOf(list);
        }
    }
}
