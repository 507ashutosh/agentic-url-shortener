package com.agentic.orchestrator.engine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * How the engine governs one stage: how many attempts it gets, the pause between them,
 * the gates around it, and how to undo its effects. Immutable; the with* methods return copies.
 */
public record StageControls(int maxAttempts, Duration retryBackoff, List<Gate> entryGates,
                            List<Gate> exitGates, RollbackHandler rollback) {

    public StageControls {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        entryGates = List.copyOf(entryGates);
        exitGates = List.copyOf(exitGates);
    }

    /** One attempt, no gates, nothing to roll back. */
    public static StageControls defaults() {
        return new StageControls(1, Duration.ZERO, List.of(), List.of(), RollbackHandler.NONE);
    }

    public StageControls withRetries(int maxAttempts, Duration backoff) {
        return new StageControls(maxAttempts, backoff, entryGates, exitGates, rollback);
    }

    public StageControls withEntryGate(Gate gate) {
        return new StageControls(maxAttempts, retryBackoff, plus(entryGates, gate), exitGates, rollback);
    }

    public StageControls withExitGate(Gate gate) {
        return new StageControls(maxAttempts, retryBackoff, entryGates, plus(exitGates, gate), rollback);
    }

    public StageControls withRollback(RollbackHandler handler) {
        return new StageControls(maxAttempts, retryBackoff, entryGates, exitGates, handler);
    }

    private static List<Gate> plus(List<Gate> gates, Gate gate) {
        List<Gate> copy = new ArrayList<>(gates);
        copy.add(gate);
        return copy;
    }
}
