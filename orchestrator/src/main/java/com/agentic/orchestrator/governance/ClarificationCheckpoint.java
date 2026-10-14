package com.agentic.orchestrator.governance;

import java.time.Duration;
import java.util.List;

import com.agentic.orchestrator.agent.Agent;
import com.agentic.orchestrator.governance.approval.ApprovalDecision;
import com.agentic.orchestrator.governance.approval.ApprovalGateway;
import com.agentic.orchestrator.governance.approval.ApprovalRequest;
import com.agentic.orchestrator.run.Decision;
import com.agentic.orchestrator.run.RunContext;

/**
 * The "clarify" stage: a human checkpoint after requirements. If the requirements agent raised
 * open questions, a human either accepts its assumptions, answers the questions (the requirements
 * agent then runs again with the answers), or rejects the requirement. Unattended runs accept the
 * assumptions, which stay in the decision log.
 */
public final class ClarificationCheckpoint {

    public static final String STAGE_ID = "clarify";
    public static final String POLICY_ID = "governance/requirements-clarification";

    private final Agent requirementsAgent;
    private final ApprovalGateway approvals;

    public ClarificationCheckpoint(Agent requirementsAgent, ApprovalGateway approvals) {
        this.requirementsAgent = requirementsAgent;
        this.approvals = approvals;
    }

    public String run(RunContext context) {
        List<Decision> requirementsDecisions = context.decisions().stream()
                .filter(d -> d.stageId().equals(requirementsAgent.id())).toList();
        List<Decision> questions = requirementsDecisions.stream()
                .filter(d -> d.kind() == Decision.Kind.QUESTION).toList();
        if (questions.isEmpty()) {
            return "no open questions";
        }

        StringBuilder evidence = new StringBuilder("Open questions:");
        questions.forEach(q -> evidence.append("\n  ? ").append(q.text()));
        evidence.append("\nAssumptions the agents will work with if you accept:");
        requirementsDecisions.stream().filter(d -> d.kind() == Decision.Kind.ASSUMPTION)
                .forEach(a -> evidence.append("\n  • ").append(a.text()));
        evidence.append("\n[y] accept the assumptions · [r] answer the questions · [n] reject the requirement");

        ApprovalRequest request = new ApprovalRequest(STAGE_ID, POLICY_ID,
                questions.size() + " open question" + (questions.size() == 1 ? "" : "s") + " on the requirement",
                evidence.toString(), List.of(requirementsAgent.id()));
        long asked = System.nanoTime();
        ApprovalDecision decision = approvals.request(request);
        context.recordApproval(request, decision, Duration.ofNanos(System.nanoTime() - asked));

        return switch (decision.outcome()) {
            case APPROVED -> questions.size() + " question(s) left open; assumptions accepted by " + decision.approver();
            case REJECTED -> throw new IllegalStateException("requirement rejected by " + decision.approver());
            case REVISE -> {
                context.addFeedback(requirementsAgent.id(), decision.feedback());
                String clarified = requirementsAgent.run(context);
                yield "clarified by " + decision.approver() + "; requirements re-run: " + clarified;
            }
        };
    }
}
