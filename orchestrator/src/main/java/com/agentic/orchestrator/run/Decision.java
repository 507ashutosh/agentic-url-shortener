package com.agentic.orchestrator.run;

import java.util.List;

/**
 * One entry in the decision lineage: what a stage decided or assumed, why, and which
 * earlier stages' outputs it was based on.
 */
public record Decision(String stageId, Kind kind, String text, String rationale, List<String> basedOn) {

    public enum Kind {
        DECISION, ASSUMPTION, QUESTION
    }

    public Decision {
        basedOn = List.copyOf(basedOn);
    }
}
