package com.agentic.orchestrator.engine;

import java.util.function.Function;

/**
 * A check attached to a stage. Entry gates run before the stage starts;
 * exit gates run after each attempt and decide whether its output is accepted.
 */
public interface Gate {

    String name();

    GateDecision check(Stage stage);

    static Gate of(String name, Function<Stage, GateDecision> check) {
        return new Gate() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public GateDecision check(Stage stage) {
                return check.apply(stage);
            }
        };
    }
}
