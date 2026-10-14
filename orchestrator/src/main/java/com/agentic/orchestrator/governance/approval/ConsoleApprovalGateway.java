package com.agentic.orchestrator.governance.approval;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Asks the person at the terminal. While the question is open, other stages' output is held back
 * (both use the same PrintStream as a lock), so the prompt is never interleaved with progress lines.
 */
public final class ConsoleApprovalGateway implements ApprovalGateway {

    private static final int MAX_EVIDENCE_LINES = 60;

    private final PrintStream out;
    private final BufferedReader in;
    private final String approver;

    public ConsoleApprovalGateway(PrintStream out, BufferedReader in, String approver) {
        this.out = out;
        this.in = in;
        this.approver = approver;
    }

    @Override
    public ApprovalDecision request(ApprovalRequest request) {
        synchronized (out) {
            out.printf("%n  ⏸ approval required · %s · %s%n", request.stageId(), request.title());
            out.printf("    policy: %s%n", request.policyId());
            // The reviewer must see the whole change set; only very long evidence is cut, and the cut is stated.
            List<String> lines = request.evidence().lines().toList();
            int shown = lines.size() <= MAX_EVIDENCE_LINES ? lines.size() : MAX_EVIDENCE_LINES - 1;
            lines.subList(0, shown).forEach(line -> out.println("      " + line));
            if (shown < lines.size()) {
                out.printf("      … %d more lines (full detail in the stage report)%n", lines.size() - shown);
            }
            boolean canRevise = !request.revisableStages().isEmpty();
            while (true) {
                out.print(canRevise ? "    approve? [y]es / [n]o / [r]evise: " : "    approve? [y/n]: ");
                out.flush();
                String answer = readLine().strip().toLowerCase();
                switch (answer) {
                    case "y", "yes" -> {
                        out.printf("    ✓ approved by %s%n%n", approver);
                        return ApprovalDecision.approved(approver);
                    }
                    case "n", "no" -> {
                        out.printf("    ✗ rejected by %s%n%n", approver);
                        return ApprovalDecision.rejected(approver);
                    }
                    case "r", "revise" -> {
                        if (canRevise) {
                            return revise(request);
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    private ApprovalDecision revise(ApprovalRequest request) {
        String stage;
        do {
            out.printf("    which stage should change? %s: ", request.revisableStages());
            out.flush();
            stage = readLine().strip();
        } while (!request.revisableStages().contains(stage));
        out.print("    what should change? ");
        out.flush();
        String feedback = readLine().strip();
        out.printf("    ↺ revision of '%s' requested by %s%n%n", stage, approver);
        return ApprovalDecision.revise(approver, stage, feedback);
    }

    private String readLine() {
        try {
            String line = in.readLine();
            if (line == null) {
                // No terminal input (e.g. piped run): never approve by default.
                return "n";
            }
            return line;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
