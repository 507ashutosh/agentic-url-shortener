package com.agentic.orchestrator.engine;

/** The work a stage does. Returns a one-line summary of what it produced; throws to signal failure. */
@FunctionalInterface
public interface StageAction {

    String execute() throws Exception;
}
