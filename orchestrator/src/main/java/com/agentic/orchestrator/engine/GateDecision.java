package com.agentic.orchestrator.engine;

/** The answer of a gate: let the stage through, reject this attempt, or stop the whole run. */
public record GateDecision(Verdict verdict, String reason) {

    public enum Verdict {
        /** Continue. */
        PASS,
        /** This attempt's output is not acceptable: undo it and try again if attempts remain. */
        RETRY,
        /** Do not continue at all (e.g. a human rejected it): undo the stage and safe-stop the run. */
        STOP
    }

    public static GateDecision pass() {
        return new GateDecision(Verdict.PASS, null);
    }

    public static GateDecision retry(String reason) {
        return new GateDecision(Verdict.RETRY, reason);
    }

    public static GateDecision stop(String reason) {
        return new GateDecision(Verdict.STOP, reason);
    }

    public boolean passed() {
        return verdict == Verdict.PASS;
    }
}
