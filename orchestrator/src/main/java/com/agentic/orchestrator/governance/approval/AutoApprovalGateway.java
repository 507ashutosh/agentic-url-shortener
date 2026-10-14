package com.agentic.orchestrator.governance.approval;

/**
 * Approves everything, for unattended runs (--auto-approve) and tests.
 * The approver is recorded as "auto", so the audit trail shows no human looked.
 */
public final class AutoApprovalGateway implements ApprovalGateway {

    @Override
    public ApprovalDecision request(ApprovalRequest request) {
        return ApprovalDecision.approved("auto (--auto-approve)");
    }
}
