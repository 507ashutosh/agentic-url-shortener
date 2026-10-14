package com.agentic.orchestrator.governance.policy;

/**
 * A policy result that needs action.
 *
 * @param evidence what a reviewer should look at, e.g. the offending lines
 */
public record PolicyFinding(String policyId, Severity severity, String reason, String evidence) {

    public enum Severity {
        /** Allowed only with a human's explicit approval (change control). */
        REQUIRES_APPROVAL,
        /** Never allowed: the output is rejected. */
        BLOCK
    }
}
