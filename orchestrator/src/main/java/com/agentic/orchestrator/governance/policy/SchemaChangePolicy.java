package com.agentic.orchestrator.governance.policy;

import java.util.ArrayList;
import java.util.List;

import com.agentic.orchestrator.governance.policy.PolicyFinding.Severity;
import com.agentic.orchestrator.run.ChangedFile;

/** Change control: any database schema change needs a human's approval. */
public final class SchemaChangePolicy implements Policy {

    @Override
    public String id() {
        return "change-control/db-migration";
    }

    @Override
    public List<PolicyFinding> evaluate(List<ChangedFile> changes) {
        List<PolicyFinding> findings = new ArrayList<>();
        for (ChangedFile file : changes) {
            if (file.path().endsWith(".sql")) {
                StringBuilder evidence = new StringBuilder(file.path());
                file.removedLines().forEach(l -> evidence.append("\n- ").append(l.strip()));
                file.addedLines().forEach(l -> evidence.append("\n+ ").append(l.strip()));
                findings.add(new PolicyFinding(id(), Severity.REQUIRES_APPROVAL,
                        "database schema change", evidence.toString()));
            }
        }
        return findings;
    }
}
