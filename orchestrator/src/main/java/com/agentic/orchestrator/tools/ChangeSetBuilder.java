package com.agentic.orchestrator.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.agentic.orchestrator.run.Artifact;
import com.agentic.orchestrator.run.RunContext;

/**
 * The release stage. Not an AI agent: it compares the workspace with the real repository and
 * writes the difference as a patch a human can review. Nothing is applied to the real code here.
 */
public final class ChangeSetBuilder {

    public static final String STAGE_ID = "release";

    // The parts of the repository agents are able to change.
    private static final List<String> TRACKED = List.of("shortener/src", "shortener/pom.xml", "docs");

    public String run(RunContext context) throws IOException, InterruptedException {
        Path repo = context.workspace().repoRoot();
        Path workspace = context.workspace().workspaceDir();

        List<String> added = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        List<String> deleted = new ArrayList<>();
        StringBuilder patch = new StringBuilder();
        for (String file : allFiles(repo, workspace)) {
            Path before = repo.resolve(file);
            Path after = workspace.resolve(file);
            if (!Files.exists(before)) {
                added.add(file);
            } else if (!Files.exists(after)) {
                deleted.add(file);
            } else if (Files.mismatch(before, after) != -1) {
                modified.add(file);
            } else {
                continue;
            }
            patch.append(diff(repo, workspace, file));
        }

        Path patchFile = context.workspace().runDir().resolve("changes.patch");
        context.workspace().write(patchFile, patch.toString());

        String summary = "%d added, %d modified, %d deleted · patch: %s".formatted(
                added.size(), modified.size(), deleted.size(), repo.relativize(patchFile));
        StringBuilder body = new StringBuilder("Change set for review (not yet applied to the repository).\n\n");
        added.forEach(f -> body.append("- added `").append(f).append("`\n"));
        modified.forEach(f -> body.append("- modified `").append(f).append("`\n"));
        deleted.forEach(f -> body.append("- deleted `").append(f).append("`\n"));
        context.record(new Artifact(STAGE_ID, summary, body.toString(), List.of()), List.of());
        return summary;
    }

    private static Set<String> allFiles(Path repo, Path workspace) throws IOException {
        Set<String> files = new TreeSet<>();
        for (Path root : List.of(repo, workspace)) {
            for (String tracked : TRACKED) {
                Path start = root.resolve(tracked);
                if (!Files.exists(start)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(start)) {
                    walk.filter(Files::isRegularFile).forEach(p -> files.add(root.relativize(p).toString()));
                }
            }
        }
        return files;
    }

    /** A unified diff of one file, as `git diff` shows it: a/&lt;file&gt; is the repository, b/&lt;file&gt; the workspace. */
    private static String diff(Path repo, Path workspace, String file) throws IOException, InterruptedException {
        String workspaceCopy = repo.relativize(workspace.resolve(file)).toString();
        String before = Files.exists(repo.resolve(file)) ? file : "/dev/null";
        String after = Files.exists(workspace.resolve(file)) ? workspaceCopy : "/dev/null";
        Process process = new ProcessBuilder("git", "diff", "--no-index", "--no-color", before, after)
                .directory(repo.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor();   // exit code 1 just means "files differ"
        // Label the workspace copy with its repository path, so the patch applies with `git apply`.
        return output.replace(workspaceCopy, file);
    }
}
