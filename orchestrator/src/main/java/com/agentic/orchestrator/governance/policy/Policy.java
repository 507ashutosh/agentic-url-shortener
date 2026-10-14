package com.agentic.orchestrator.governance.policy;

import java.util.List;

import com.agentic.orchestrator.run.ChangedFile;

/**
 * A rule checked against the files a stage actually changed, read from disk,
 * so it does not depend on what the agent claims it did.
 */
public interface Policy {

    /** Stable identifier used in approvals and the audit log, e.g. "security/secret-scan". */
    String id();

    /** Returns one finding per problem; an empty list means the change complies. */
    List<PolicyFinding> evaluate(List<ChangedFile> changes);
}
