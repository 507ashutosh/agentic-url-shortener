package com.agentic.orchestrator.scenario;

/** A requirement to run through the pipeline, loaded from scenarios/&lt;name&gt;.yaml. */
public record Scenario(String name, String type, String title, String requirement) {
}
