package com.agentic.orchestrator.run;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The folder for one run, under runs/&lt;run-id&gt;/:
 * <pre>
 * workspace/   a private copy of the shortener that agents change; the real code is never touched
 * artifacts/   one report per stage, plus logs
 * </pre>
 */
public final class RunWorkspace {

    // What gets copied from the repository into the workspace.
    private static final List<String> BASELINE = List.of("pom.xml", "mvnw", "mvnw.cmd", ".mvn", "shortener");
    private static final Set<String> SKIPPED_DIRS = Set.of("target", ".git", ".idea");

    private final String runId;
    private final Path repoRoot;
    private final Path runDir;
    private final Path workspaceDir;
    private final Path artifactsDir;

    private RunWorkspace(String runId, Path repoRoot, Path runDir) {
        this.runId = runId;
        this.repoRoot = repoRoot;
        this.runDir = runDir;
        this.workspaceDir = runDir.resolve("workspace");
        this.artifactsDir = runDir.resolve("artifacts");
    }

    /** Creates runs/&lt;run-id&gt;/ and copies the current shortener into its workspace. */
    public static RunWorkspace create(Path repoRoot, String scenarioName) {
        String runId = "run-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + scenarioName;
        RunWorkspace ws = new RunWorkspace(runId, repoRoot, repoRoot.resolve("runs").resolve(runId));
        try {
            Files.createDirectories(ws.artifactsDir);
            for (String entry : BASELINE) {
                copy(repoRoot.resolve(entry), ws.workspaceDir.resolve(entry));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create workspace for " + runId, e);
        }
        return ws;
    }

    public String runId() {
        return runId;
    }

    public Path repoRoot() {
        return repoRoot;
    }

    public Path runDir() {
        return runDir;
    }

    public Path workspaceDir() {
        return workspaceDir;
    }

    public Path artifactsDir() {
        return artifactsDir;
    }

    /** All text files of the shortener in the workspace, for agents that need to read the code. */
    public String codebaseSnapshot() {
        Path shortener = workspaceDir.resolve("shortener");
        StringBuilder snapshot = new StringBuilder();
        try (Stream<Path> files = Files.walk(shortener)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> SKIPPED_DIRS.stream().noneMatch(d -> shortener.relativize(p).startsWith(d)))
                    .sorted()
                    .forEach(p -> snapshot.append("=== ").append(workspaceDir.relativize(p)).append(" ===\n")
                            .append(read(p)).append("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return snapshot.toString();
    }

    void saveArtifact(Artifact artifact, List<Decision> decisions) {
        StringBuilder md = new StringBuilder()
                .append("# ").append(artifact.stageId()).append("\n\n")
                .append("**Summary:** ").append(artifact.summary()).append("\n\n");
        if (!decisions.isEmpty()) {
            md.append("## Decisions and assumptions\n\n");
            for (Decision d : decisions) {
                md.append("- **").append(d.kind()).append(":** ").append(d.text());
                if (d.rationale() != null) {
                    md.append(" — because ").append(d.rationale());
                }
                md.append(d.basedOn().isEmpty() ? "" : " _(based on: " + String.join(", ", d.basedOn()) + ")_")
                        .append("\n");
            }
            md.append("\n");
        }
        if (!artifact.filesWritten().isEmpty()) {
            md.append("## Files written\n\n");
            artifact.filesWritten().forEach(f -> md.append("- `").append(f).append("`\n"));
            md.append("\n");
        }
        if (!artifact.body().isBlank()) {
            md.append("## Report\n\n").append(artifact.body().strip()).append("\n");
        }
        write(artifactsDir.resolve(artifact.stageId() + ".md"), md.toString());
    }

    public void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file, e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copy(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path relative = source.relativize(path);
                if (relative.getNameCount() > 0 && SKIPPED_DIRS.contains(relative.getName(0).toString())) {
                    continue;
                }
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    // COPY_ATTRIBUTES keeps the executable bit on mvnw.
                    Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }
}
