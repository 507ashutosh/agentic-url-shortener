package com.agentic.orchestrator.engine;

/**
 * Makes a stage's side effects all-or-nothing, like a transaction:
 * {@link #commit} after an accepted attempt, {@link #rollback} after a failed or rejected one.
 */
public interface RollbackHandler {

    RollbackHandler NONE = new RollbackHandler() {
        @Override
        public void commit(String stageId) {
        }

        @Override
        public String rollback(String stageId) {
            return null;
        }
    };

    void commit(String stageId);

    /** Undoes the stage's uncommitted effects. Returns what was undone, or null if there was nothing. */
    String rollback(String stageId);
}
