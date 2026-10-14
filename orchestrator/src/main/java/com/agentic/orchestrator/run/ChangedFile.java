package com.agentic.orchestrator.run;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One file a stage changed, relative to the workspace root.
 *
 * @param before content before the change, or null if the file is new
 * @param after  content after the change, or null if the file was deleted
 */
public record ChangedFile(String path, String before, String after) {

    public boolean isNew() {
        return before == null;
    }

    public boolean isDeleted() {
        return after == null;
    }

    /** Lines present after the change that were not there before. */
    public List<String> addedLines() {
        if (after == null) {
            return List.of();
        }
        Set<String> old = before == null ? Set.of() : new HashSet<>(Arrays.asList(before.split("\n")));
        return Arrays.stream(after.split("\n")).filter(line -> !old.contains(line)).toList();
    }

    /** Lines that were there before and are gone after the change. */
    public List<String> removedLines() {
        if (before == null) {
            return List.of();
        }
        Set<String> now = after == null ? Set.of() : new HashSet<>(Arrays.asList(after.split("\n")));
        return Arrays.stream(before.split("\n")).filter(line -> !now.contains(line)).toList();
    }
}
