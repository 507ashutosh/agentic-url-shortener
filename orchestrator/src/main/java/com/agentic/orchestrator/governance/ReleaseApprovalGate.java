package com.agentic.orchestrator.governance;

import java.util.List;

import com.agentic.orchestrator.engine.Gate;
import com.agentic.orchestrator.engine.GateDecision;
import com.agentic.orchestrator.engine.Stage;
import com.agentic.orchestrator.governance.approval.ApprovalDecision;
import com.agentic.orchestrator.governance.approval.ApprovalGateway;
import com.agentic.orchestrator.governance.approval.ApprovalRequest;
import com.agentic.orchestrator.run.Artifact;
import com.agentic.orchestrator.run.RunContext;

/**
 * Exit gate on the release stage: a human signs off the complete change set.
 * They can approve, reject (safe-stop), or send one stage back with feedback, which triggers re-planning.
 */
public final class ReleaseApprovalGate implements Gate {

    public static final String POLICY_ID = "change-control/release";

    private final ApprovalGateway approvals;
    private final RunContext context;
    private final List<String> revisableStages;

    public ReleaseApprovalGate(ApprovalGateway approvals, RunContext context, List<String> revisableStages) {
        this.approvals = approvals;
        this.context = context;
        this.revisableStages = List.copyOf(revisableStages);
    }

    @Override
    public String name() {
        return "release-approval";
    }

    @Override
    public GateDecision check(Stage stage) {
        String validation = context.artifact("validate").map(Artifact::summary).orElse("not validated");
        String changes = context.artifact(stage.id()).map(a -> a.summary() + "\n" + a.body().strip()).orElse("");
        ApprovalRequest request = new ApprovalRequest(stage.id(), POLICY_ID, "release this change set",
                "validation: " + validation + "\n" + changes, revisableStages);

        ApprovalDecision decision = approvals.request(request);
        context.recordApproval(request, decision);
        return switch (decision.outcome()) {
            case APPROVED -> GateDecision.pass();
            case REJECTED -> GateDecision.stop("release rejected by " + decision.approver());
            case REVISE -> {
                context.requestRevision(decision.reviseStage(), decision.feedback());
                yield GateDecision.stop("revision of '" + decision.reviseStage() + "' requested by "
                        + decision.approver());
            }
        };
    }
}
