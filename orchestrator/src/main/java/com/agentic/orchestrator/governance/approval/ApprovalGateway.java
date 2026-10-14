package com.agentic.orchestrator.governance.approval;

/** Where approval requests go: a person at the terminal, or an automatic policy for unattended runs. */
public interface ApprovalGateway {

    ApprovalDecision request(ApprovalRequest request);
}
