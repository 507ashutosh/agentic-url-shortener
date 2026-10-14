package com.agentic.orchestrator.agent;

import java.util.List;

import com.agentic.orchestrator.run.RunContext;

/** A worker that does one SDLC job, reading earlier results from the shared run context. */
public interface Agent {

    /** The pipeline stage this agent fills, e.g. "design". */
    String id();

    /** Does the work, records its artifact and decisions in the context, and returns a one-line summary. */
    String run(RunContext context);

    /** Workspace folders this agent may write to. Empty means it may not write files at all. */
    List<String> writableFolders();
}
