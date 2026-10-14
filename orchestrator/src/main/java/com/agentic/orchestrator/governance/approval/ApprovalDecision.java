package com.agentic.orchestrator.governance.approval;

import java.time.Instant;

/**
 * A human's answer to an approval request, kept for the audit trail.
 *
 * @param reviseStage for REVISE: the stage to run again
 * @param feedback    for REVISE: what should change; otherwise an optional comment
 */
public record ApprovalDecision(Outcome outcome, String approver, Instant decidedAt,
                               String reviseStage, String feedback) {

    public enum Outcome {
        APPROVED, REJECTED, REVISE
    }

    public static ApprovalDecision approved(String approver) {
        return new ApprovalDecision(Outcome.APPROVED, approver, Instant.now(), null, null);
    }

    public static ApprovalDecision rejected(String approver) {
        return new ApprovalDecision(Outcome.REJECTED, approver, Instant.now(), null, null);
    }

    public static ApprovalDecision revise(String approver, String stage, String feedback) {
        return new ApprovalDecision(Outcome.REVISE, approver, Instant.now(), stage, feedback);
    }
}
