package com.agentic.orchestrator.governance.policy;

import java.util.List;

import com.agentic.orchestrator.governance.policy.PolicyFinding.Severity;
import com.agentic.orchestrator.run.ChangedFile;

/** Compliance: a new or changed third-party dependency needs a human's approval (licensing, supply chain). */
public final class DependencyChangePolicy implements Policy {

    @Override
    public String id() {
        return "compliance/dependency-change";
    }

    @Override
    public List<PolicyFinding> evaluate(List<ChangedFile> changes) {
        return changes.stream()
                .filter(f -> f.path().endsWith("pom.xml"))
                .map(f -> new PolicyFinding(id(), Severity.REQUIRES_APPROVAL, "build or dependency change",
                        f.path() + "\n+ " + String.join("\n+ ", f.addedLines().stream().map(String::strip).toList())))
                .toList();
    }
}
