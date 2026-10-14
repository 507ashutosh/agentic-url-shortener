package com.agentic.orchestrator.governance.policy;

import java.util.List;

import com.agentic.orchestrator.governance.policy.PolicyFinding.Severity;
import com.agentic.orchestrator.run.ChangedFile;

/** Change control: an unusually large change from one stage needs a human's approval. */
public final class BlastRadiusPolicy implements Policy {

    private final int maxFiles;

    public BlastRadiusPolicy(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    @Override
    public String id() {
        return "change-control/blast-radius";
    }

    @Override
    public List<PolicyFinding> evaluate(List<ChangedFile> changes) {
        if (changes.size() <= maxFiles) {
            return List.of();
        }
        return List.of(new PolicyFinding(id(), Severity.REQUIRES_APPROVAL,
                changes.size() + " files changed (limit " + maxFiles + ")",
                String.join("\n", changes.stream().map(ChangedFile::path).toList())));
    }
}
