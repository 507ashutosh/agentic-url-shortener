package com.agentic.orchestrator.engine;

import java.util.List;

/** One node in the pipeline: a name, the stages it waits for, the work it does, and how it is governed. */
public record Stage(String id, List<String> dependsOn, StageAction action, StageControls controls) {

    public Stage {
        dependsOn = List.copyOf(dependsOn);
    }
}
