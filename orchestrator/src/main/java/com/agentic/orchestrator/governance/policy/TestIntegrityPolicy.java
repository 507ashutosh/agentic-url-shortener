package com.agentic.orchestrator.governance.policy;

import java.util.ArrayList;
import java.util.List;

import com.agentic.orchestrator.governance.policy.PolicyFinding.Severity;
import com.agentic.orchestrator.run.ChangedFile;

/** Compliance: agents may add tests but never delete, disable or thin out existing ones. */
public final class TestIntegrityPolicy implements Policy {

    @Override
    public String id() {
        return "compliance/test-integrity";
    }

    @Override
    public List<PolicyFinding> evaluate(List<ChangedFile> changes) {
        List<PolicyFinding> findings = new ArrayList<>();
        for (ChangedFile file : changes) {
            if (!file.path().contains("/src/test/")) {
                continue;
            }
            if (file.isDeleted()) {
                findings.add(block(file, "existing test file deleted"));
            } else if (file.addedLines().stream().anyMatch(l -> l.strip().startsWith("@Disabled"))) {
                findings.add(block(file, "test disabled with @Disabled"));
            } else if (!file.isNew() && countTests(file.after()) < countTests(file.before())) {
                findings.add(block(file, "existing tests removed (" + countTests(file.before()) + " → "
                        + countTests(file.after()) + ")"));
            }
        }
        return findings;
    }

    private PolicyFinding block(ChangedFile file, String reason) {
        return new PolicyFinding(id(), Severity.BLOCK, reason + " in " + file.path(), file.path());
    }

    private static long countTests(String content) {
        return content.lines().map(String::strip)
                .filter(l -> l.startsWith("@Test") || l.startsWith("@ParameterizedTest")).count();
    }
}
