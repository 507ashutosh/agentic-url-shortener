package com.agentic.orchestrator.agent;

import java.util.ArrayList;
import java.util.List;

import com.agentic.orchestrator.agent.AgentOutput.FileChange;
import com.agentic.orchestrator.agent.AgentOutput.ParsedDecision;
import com.agentic.orchestrator.run.Decision.Kind;

/**
 * Parses the plain-text response format every agent must use.
 * A text format rather than JSON, because code inside JSON strings needs escaping that models
 * (and humans reading the recorded responses) get wrong.
 */
public final class AgentOutputParser {

    /** Included in every agent's system prompt. */
    public static final String FORMAT = """
            Respond in exactly this plain-text format:

            SUMMARY: <one line describing what you produced>
            DECISION: <a decision you made> | BECAUSE: <why>
            ASSUMPTION: <something you assumed because the requirement did not say>
            QUESTION: <something a human should clarify>

            Then your report in Markdown.

            To create or replace a file, give its COMPLETE new content (never a fragment or a diff):
            <<<FILE path/relative/to/workspace
            ...file content...
            >>>

            SUMMARY is required. DECISION, ASSUMPTION, QUESTION and FILE blocks may appear any number of times.
            """;

    private static final String FILE_START = "<<<FILE ";
    private static final String FILE_END = ">>>";

    private AgentOutputParser() {
    }

    public static AgentOutput parse(String response) {
        String summary = null;
        List<ParsedDecision> decisions = new ArrayList<>();
        List<FileChange> files = new ArrayList<>();
        StringBuilder body = new StringBuilder();

        String filePath = null;
        StringBuilder fileContent = null;
        for (String line : response.split("\n", -1)) {
            if (filePath != null) {
                if (line.strip().equals(FILE_END)) {
                    files.add(new FileChange(filePath, fileContent.toString()));
                    filePath = null;
                } else {
                    fileContent.append(line).append("\n");
                }
            } else if (line.startsWith(FILE_START)) {
                filePath = line.substring(FILE_START.length()).strip();
                fileContent = new StringBuilder();
            } else if (line.startsWith("SUMMARY:") && summary == null) {
                summary = after(line, "SUMMARY:");
            } else if (line.startsWith("DECISION:")) {
                String[] parts = after(line, "DECISION:").split("\\|\\s*BECAUSE:", 2);
                decisions.add(new ParsedDecision(Kind.DECISION, parts[0].strip(),
                        parts.length > 1 ? parts[1].strip() : null));
            } else if (line.startsWith("ASSUMPTION:")) {
                decisions.add(new ParsedDecision(Kind.ASSUMPTION, after(line, "ASSUMPTION:"), null));
            } else if (line.startsWith("QUESTION:")) {
                decisions.add(new ParsedDecision(Kind.QUESTION, after(line, "QUESTION:"), null));
            } else {
                body.append(line).append("\n");
            }
        }

        if (filePath != null) {
            throw new AgentOutputException("File block for '" + filePath + "' is not closed with " + FILE_END);
        }
        if (summary == null || summary.isBlank()) {
            throw new AgentOutputException("Response has no SUMMARY line");
        }
        return new AgentOutput(summary, decisions, body.toString().strip(), files);
    }

    private static String after(String line, String prefix) {
        return line.substring(prefix.length()).strip();
    }
}
