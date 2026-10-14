package com.agentic.orchestrator.scenario;

/**
 * A requirement to run through the pipeline, loaded from scenarios/&lt;name&gt;.yaml.
 *
 * @param baseline what the agents start from: "repo" (the current shortener) or
 *                 "scaffold" (only the build files, no source code: a greenfield start)
 */
public record Scenario(String name, String type, String title, String requirement, String baseline) {

    public boolean startsFromScaffold() {
        return "scaffold".equals(baseline);
    }
}
