package com.agentic.orchestrator.engine;

import java.util.List;

/** One node in the pipeline: a name, the stages it waits for, and the work it does. */
public record Stage(String id, List<String> dependsOn, StageAction action) {

    public Stage {
        dependsOn = List.copyOf(dependsOn);
    }
}
