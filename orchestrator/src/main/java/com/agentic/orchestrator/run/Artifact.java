package com.agentic.orchestrator.run;

import java.util.List;

/**
 * What one stage produced: a one-line summary, the full report, and any files it wrote.
 * Later stages read earlier artifacts; that is how context flows through the pipeline.
 */
public record Artifact(String stageId, String summary, String body, List<String> filesWritten) {

    public Artifact {
        filesWritten = List.copyOf(filesWritten);
    }
}
