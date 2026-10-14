package com.agentic.orchestrator.governance.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.agentic.orchestrator.governance.policy.PolicyFinding.Severity;
import com.agentic.orchestrator.run.ChangedFile;

/** Security: blocks any change that adds something that looks like a credential. */
public final class SecretScanPolicy implements Policy {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("sk-ant-[A-Za-z0-9_-]{20,}"),                         // Anthropic API key
            Pattern.compile("AKIA[0-9A-Z]{16}"),                                  // AWS access key id
            Pattern.compile("ghp_[A-Za-z0-9]{36}"),                               // GitHub token
            Pattern.compile("-----BEGIN ([A-Z]+ )?PRIVATE KEY-----"),             // private key
            Pattern.compile("(?i)(password|passwd|secret|api[_-]?key|token)\\s*[=:]\\s*[\"'][^\"'\\s]{8,}[\"']"));

    @Override
    public String id() {
        return "security/secret-scan";
    }

    @Override
    public List<PolicyFinding> evaluate(List<ChangedFile> changes) {
        List<PolicyFinding> findings = new ArrayList<>();
        for (ChangedFile file : changes) {
            for (String line : file.addedLines()) {
                for (Pattern pattern : PATTERNS) {
                    if (pattern.matcher(line).find()) {
                        findings.add(new PolicyFinding(id(), Severity.BLOCK,
                                "possible secret added in " + file.path(),
                                file.path() + ": " + mask(line.strip())));
                        break;
                    }
                }
            }
        }
        return findings;
    }

    // Never repeat a secret in logs or the audit trail.
    private static String mask(String line) {
        return line.length() <= 12 ? "****" : line.substring(0, 12) + "****";
    }
}
