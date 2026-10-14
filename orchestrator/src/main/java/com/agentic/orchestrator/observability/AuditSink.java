package com.agentic.orchestrator.observability;

import java.util.LinkedHashMap;
import java.util.Map;

/** Where governance events (decisions, approvals, policy findings, fallbacks) are recorded. */
public interface AuditSink {

    AuditSink NONE = (type, stageId, data) -> {
    };

    /**
     * @param type    event type, e.g. "approval" or "policy.finding"
     * @param stageId the stage it concerns, or null for run-level events
     * @param data    event details; values must be JSON-friendly (strings, numbers, booleans, lists, maps)
     */
    void record(String type, String stageId, Map<String, Object> data);

    /** Builds event data from alternating keys and values, keeping their order. Values may be null. */
    static Map<String, Object> data(Object... keysAndValues) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            data.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return data;
    }
}
