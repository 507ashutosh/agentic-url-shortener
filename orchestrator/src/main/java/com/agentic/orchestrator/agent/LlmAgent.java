package com.agentic.orchestrator.agent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.agentic.orchestrator.agent.AgentOutput.FileChange;
import com.agentic.orchestrator.llm.LlmClient;
import com.agentic.orchestrator.llm.LlmRequest;
import com.agentic.orchestrator.run.Artifact;
import com.agentic.orchestrator.run.Decision;
import com.agentic.orchestrator.run.RunContext;

/**
 * The shared steps of every LLM-backed agent:
 * build a prompt from the requirement, upstream artifacts and (optionally) the code;
 * call the model; parse the answer; write files inside the allowed folders; record the result.
 * Subclasses only say what their job is and what they may read and write.
 */
public abstract class LlmAgent implements Agent {

    private final LlmClient llm;

    protected LlmAgent(LlmClient llm) {
        this.llm = llm;
    }

    /** What this agent is responsible for, in the model's instructions. */
    protected abstract String role();

    /** Earlier stages whose artifacts this agent reads. These become the lineage of its decisions. */
    protected abstract List<String> reads();

    /** Whether the prompt includes the current shortener source code. */
    protected boolean readsCodebase() {
        return false;
    }

    /** Workspace folders this agent may write to. Empty means it may not write files at all. */
    public List<String> writableFolders() {
        return List.of();
    }

    @Override
    public final String run(RunContext context) {
        String response = llm.complete(new LlmRequest(id(), systemPrompt(), userPrompt(context)));
        AgentOutput output = AgentOutputParser.parse(response);

        List<String> written = writeFiles(output.files(), context);
        List<Decision> decisions = output.decisions().stream()
                .map(d -> new Decision(id(), d.kind(), d.text(), d.rationale(), reads()))
                .toList();
        context.record(new Artifact(id(), output.summary(), output.body(), written), decisions);
        return output.summary();
    }

    private String systemPrompt() {
        String boundary = writableFolders().isEmpty()
                ? "You must not create or change any files."
                : "You may only create or change files under: " + String.join(", ", writableFolders())
                        + ". Any other path is rejected.";
        return """
                You are the %s agent in an automated software delivery pipeline for a Spring Boot 4 / Java 25 \
                URL shortener service. Other agents handle the other stages; humans approve high-impact changes.

                Your job: %s

                %s
                Be concise and concrete. Do not invent requirements beyond what you are given; record assumptions.

                %s""".formatted(id(), role(), boundary, AgentOutputParser.FORMAT);
    }

    private String userPrompt(RunContext context) {
        StringBuilder prompt = new StringBuilder()
                .append("# Requirement\n\n").append(context.scenario().requirement()).append("\n\n");
        for (String stage : reads()) {
            context.artifact(stage).ifPresent(a -> prompt
                    .append("# Output of the ").append(stage).append(" stage\n\n")
                    .append("Summary: ").append(a.summary()).append("\n\n")
                    .append(a.body()).append("\n\n"));
        }
        List<String> feedback = context.feedbackFor(id());
        if (!feedback.isEmpty()) {
            prompt.append(com.agentic.orchestrator.llm.ScriptedLlmClient.FEEDBACK_HEADING)
                    .append(" on your previous output (you must address it)\n\n");
            feedback.forEach(f -> prompt.append("- ").append(f).append("\n"));
            prompt.append("\n");
        }
        if (readsCodebase()) {
            prompt.append("# Current codebase\n\n").append(context.workspace().codebaseSnapshot());
        }
        return prompt.toString();
    }

    private List<String> writeFiles(List<FileChange> files, RunContext context) {
        Path root = context.workspace().workspaceDir();
        List<String> written = new ArrayList<>();
        for (FileChange file : files) {
            Path target = resolveInsideBoundary(root, file.path());
            context.workspace().write(target, file.content());
            written.add(root.relativize(target).toString());
        }
        return written;
    }

    /** Rejects absolute paths, "../" escapes and anything outside the agent's writable folders. */
    private Path resolveInsideBoundary(Path root, String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        boolean allowed = !Path.of(relativePath).isAbsolute()
                && writableFolders().stream().anyMatch(folder -> target.startsWith(root.resolve(folder)));
        if (!allowed) {
            throw new AutonomyBoundaryException(id() + " agent tried to write '" + relativePath
                    + "'; allowed folders: " + writableFolders());
        }
        return target;
    }
}
