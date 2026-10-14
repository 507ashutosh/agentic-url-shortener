package com.agentic.orchestrator.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.agentic.orchestrator.engine.Gate;
import com.agentic.orchestrator.engine.GateDecision;
import com.agentic.orchestrator.engine.Stage;
import com.agentic.orchestrator.governance.approval.ApprovalDecision;
import com.agentic.orchestrator.governance.approval.ApprovalGateway;
import com.agentic.orchestrator.governance.approval.ApprovalRequest;
import com.agentic.orchestrator.governance.policy.Policy;
import com.agentic.orchestrator.governance.policy.PolicyFinding;
import com.agentic.orchestrator.run.ChangedFile;
import com.agentic.orchestrator.run.RunContext;

/**
 * Exit gate that checks a stage's actual file changes against the policies:
 * <ul>
 *   <li>a BLOCK finding rejects the attempt (it is rolled back, and retried if attempts remain)</li>
 *   <li>a REQUIRES_APPROVAL finding asks a human; a rejection stops the run</li>
 * </ul>
 */
public final class PolicyGate implements Gate {

    private final List<Policy> policies;
    private final Supplier<List<ChangedFile>> changes;
    private final ApprovalGateway approvals;
    private final RunContext context;
    private final boolean blockStopsRun;

    /**
     * @param changes        the files to check, read fresh each time the gate runs
     * @param blockStopsRun  true where retrying cannot help (e.g. validation of the final change set)
     */
    public PolicyGate(List<Policy> policies, Supplier<List<ChangedFile>> changes, ApprovalGateway approvals,
                      RunContext context, boolean blockStopsRun) {
        this.policies = List.copyOf(policies);
        this.changes = changes;
        this.approvals = approvals;
        this.context = context;
        this.blockStopsRun = blockStopsRun;
    }

    @Override
    public String name() {
        return "policy-check";
    }

    @Override
    public GateDecision check(Stage stage) {
        List<ChangedFile> changed = changes.get();
        List<PolicyFinding> findings = new ArrayList<>();
        for (Policy policy : policies) {
            findings.addAll(policy.evaluate(changed));
        }
        findings.forEach(f -> context.recordPolicyFinding(stage.id(), f));

        for (PolicyFinding finding : findings) {
            if (finding.severity() == PolicyFinding.Severity.BLOCK) {
                String reason = finding.policyId() + ": " + finding.reason();
                return blockStopsRun ? GateDecision.stop(reason) : GateDecision.retry(reason);
            }
        }
        for (PolicyFinding finding : findings) {
            ApprovalRequest request = new ApprovalRequest(stage.id(), finding.policyId(), finding.reason(),
                    finding.evidence(), List.of());
            ApprovalDecision decision = approvals.request(request);
            context.recordApproval(request, decision);
            if (decision.outcome() != ApprovalDecision.Outcome.APPROVED) {
                return GateDecision.stop(finding.policyId() + " rejected by " + decision.approver());
            }
        }
        return GateDecision.pass();
    }
}
