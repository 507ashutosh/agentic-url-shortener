package com.agentic.orchestrator.run;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Snapshots and rollback for the run workspace, using a private git repository inside it.
 * The baseline (the code before any agent ran) is the first commit; each accepted stage adds one.
 *
 * <p>Stages write to separate folders, so each stage is committed and rolled back by folder,
 * without touching what parallel stages are doing. All git calls are serialized by one lock.
 */
public final class WorkspaceCheckpoints {

    private final Path dir;
    private String baseline;

    private WorkspaceCheckpoints(Path dir) {
        this.dir = dir;
    }

    /** Starts tracking the workspace and records its current state as the baseline. */
    public static WorkspaceCheckpoints init(Path workspaceDir) {
        WorkspaceCheckpoints checkpoints = new WorkspaceCheckpoints(workspaceDir);
        checkpoints.git("init", "-q");
        checkpoints.git("add", "-A");
        checkpoints.git("commit", "-q", "--allow-empty", "-m", "baseline");
        checkpoints.baseline = checkpoints.git("rev-parse", "HEAD").strip();
        return checkpoints;
    }

    /** Files changed in these folders since the last checkpoint, with their old and new content. */
    public synchronized List<ChangedFile> uncommittedChanges(List<String> folders) {
        return changes("HEAD", folders, true);
    }

    /** Files changed in these folders since the baseline, committed or not. */
    public synchronized List<ChangedFile> changesSinceBaseline(List<String> folders) {
        return changes(baseline, folders, true);
    }

    /** Accepts the current state of these folders as a new checkpoint. */
    public synchronized void commit(String label, List<String> folders) {
        List<String> existing = existing(folders);
        if (existing.isEmpty() || uncommittedChanges(folders).isEmpty()) {
            return;
        }
        git(concat(List.of("add", "-A", "--"), existing));
        git(concat(List.of("commit", "-q", "-m", label, "--"), existing));
    }

    /** Puts these folders back to the last checkpoint. Returns a description, or null if nothing changed. */
    public synchronized String rollback(List<String> folders) {
        return restore("HEAD", folders);
    }

    /** Puts these folders back to the baseline (used before re-planning and after failed validation). */
    public synchronized String rollbackToBaseline(List<String> folders) {
        String undone = restore(baseline, folders);
        commit("reset to baseline", folders);
        return undone;
    }

    private String restore(String revision, List<String> folders) {
        List<ChangedFile> changed = changes(revision, folders, true);
        if (changed.isEmpty()) {
            return null;
        }
        for (ChangedFile file : changed) {
            Path path = dir.resolve(file.path());
            if (file.before() == null) {
                delete(path);
            } else {
                write(path, file.before());
            }
        }
        return changed.size() + " file" + (changed.size() == 1 ? "" : "s") + " reverted";
    }

    private List<ChangedFile> changes(String revision, List<String> folders, boolean includeUntracked) {
        List<String> existingFolders = existingOrTracked(revision, folders);
        if (existingFolders.isEmpty()) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (String line : git(concat(List.of("diff", "--name-only", revision, "--"), existingFolders)).split("\n")) {
            if (!line.isBlank()) {
                paths.add(line.strip());
            }
        }
        if (includeUntracked) {
            for (String line : git(concat(List.of("ls-files", "--others", "--exclude-standard", "--"),
                    existingFolders)).split("\n")) {
                if (!line.isBlank() && !paths.contains(line.strip())) {
                    paths.add(line.strip());
                }
            }
        }
        List<ChangedFile> result = new ArrayList<>();
        for (String path : paths) {
            String before = contentAt(revision, path);
            Path current = dir.resolve(path);
            String after = Files.exists(current) ? read(current) : null;
            if (before == null && after == null || before != null && before.equals(after)) {
                continue;
            }
            result.add(new ChangedFile(path, before, after));
        }
        return result;
    }

    private String contentAt(String revision, String path) {
        Result r = run(List.of("git", "cat-file", "-e", revision + ":" + path));
        return r.exitCode() == 0 ? git("show", revision + ":" + path) : null;
    }

    // Folders that exist now or existed at the revision (a stage may have created or deleted one).
    private List<String> existingOrTracked(String revision, List<String> folders) {
        List<String> result = new ArrayList<>();
        for (String folder : folders) {
            boolean tracked = run(List.of("git", "cat-file", "-e", revision + ":" + folder)).exitCode() == 0;
            if (tracked || Files.exists(dir.resolve(folder))) {
                result.add(folder);
            }
        }
        return result;
    }

    private List<String> existing(List<String> folders) {
        return folders.stream().filter(f -> Files.exists(dir.resolve(f))
                || run(List.of("git", "cat-file", "-e", "HEAD:" + f)).exitCode() == 0).toList();
    }

    private String git(String... args) {
        return git(List.of(args));
    }

    private String git(List<String> args) {
        Result r = run(concat(List.of("git"), args));
        if (r.exitCode() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + r.output());
        }
        return r.output();
    }

    private record Result(int exitCode, String output) {
    }

    private Result run(List<String> command) {
        // A fixed identity and no signing, so commits work on any machine.
        List<String> full = new ArrayList<>(command);
        if (full.get(0).equals("git")) {
            full.addAll(1, List.of("-c", "user.name=orchestrator", "-c", "user.email=orchestrator@localhost",
                    "-c", "commit.gpgsign=false"));
        }
        try {
            Process process = new ProcessBuilder(full).directory(dir.toFile()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(1, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException("git timed out: " + command);
            }
            return new Result(process.exitValue(), output);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while running git", e);
        }
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
