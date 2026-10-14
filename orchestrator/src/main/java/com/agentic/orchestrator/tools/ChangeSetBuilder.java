package com.agentic.orchestrator.tools;

import java.nio.file.Path;
import java.util.List;

import com.agentic.orchestrator.run.Artifact;
import com.agentic.orchestrator.run.ChangedFile;
import com.agentic.orchestrator.run.RunContext;
import com.agentic.orchestrator.run.WorkspaceCheckpoints;

/**
 * The release stage. Not an AI agent: it packages everything the accepted stages changed since the
 * workspace baseline as a patch a human can review. Nothing is applied to the real code here.
 */
public final class ChangeSetBuilder {

    public static final String STAGE_ID = "release";

    // The parts of the workspace agents are able to change.
    private static final List<String> TRACKED = List.of("shortener/src", "shortener/pom.xml", "docs");

    private final WorkspaceCheckpoints checkpoints;

    public ChangeSetBuilder(WorkspaceCheckpoints checkpoints) {
        this.checkpoints = checkpoints;
    }

    public String run(RunContext context) {
        List<ChangedFile> changes = checkpoints.changesSinceBaseline(TRACKED);
        Path patchFile = context.workspace().runDir().resolve("changes.patch");
        context.workspace().write(patchFile, checkpoints.patchSinceBaseline(TRACKED));

        long added = changes.stream().filter(ChangedFile::isNew).count();
        long deleted = changes.stream().filter(ChangedFile::isDeleted).count();
        long modified = changes.size() - added - deleted;
        String summary = "%d added, %d modified, %d deleted · patch: %s".formatted(
                added, modified, deleted, context.workspace().repoRoot().relativize(patchFile));

        StringBuilder body = new StringBuilder("Change set for review (not yet applied to the repository).\n\n");
        for (ChangedFile file : changes) {
            String kind = file.isNew() ? "added" : file.isDeleted() ? "deleted" : "modified";
            body.append("- ").append(kind).append(" `").append(file.path()).append("`\n");
        }
        context.record(new Artifact(STAGE_ID, summary, body.toString(), List.of()), List.of());
        return summary;
    }
}
