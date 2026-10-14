package com.agentic.orchestrator;

import java.util.Optional;

import com.agentic.orchestrator.console.ConsoleRunListener;
import com.agentic.orchestrator.demo.DemoPipeline;
import com.agentic.orchestrator.engine.DagExecutor;
import com.agentic.orchestrator.engine.Pipeline;
import com.agentic.orchestrator.engine.RunReport;

/**
 * Command-line entry point.
 *
 * <pre>
 * java -jar orchestrator.jar                 run the demo pipeline
 * java -jar orchestrator.jar --fail tests    make one stage fail, to see dependents skipped
 * </pre>
 */
public final class OrchestratorApp {

    private OrchestratorApp() {
    }

    public static void main(String[] args) {
        Optional<String> failStage = Optional.empty();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--fail") && i + 1 < args.length) {
                failStage = Optional.of(args[++i]);
            } else {
                System.err.println("Unknown argument: " + args[i]);
                System.err.println("Usage: java -jar orchestrator.jar [--fail <stage>]");
                System.exit(2);
            }
        }

        Pipeline pipeline = DemoPipeline.create(failStage);
        RunReport report = new DagExecutor(new ConsoleRunListener(System.out)).run(pipeline);
        System.exit(report.succeeded() ? 0 : 1);
    }
}
