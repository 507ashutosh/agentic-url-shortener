package com.agentic.orchestrator.governance.approval;

import java.util.List;

/**
 * A question for a human before a high-impact action goes ahead.
 *
 * @param policyId         the rule that requires the approval, e.g. "change-control/db-migration"
 * @param evidence         what the human should look at before deciding
 * @param revisableStages  stages the human may send back with feedback; empty if revision is not offered
 */
public record ApprovalRequest(String stageId, String policyId, String title, String evidence,
                              List<String> revisableStages) {

    public ApprovalRequest {
        revisableStages = List.copyOf(revisableStages);
    }
}
